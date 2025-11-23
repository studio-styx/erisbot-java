package studio.styx.erisbot.features.interactions.economy;

import database.utils.DatabaseUtils;
import database.utils.LogManage;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import shared.Colors;
import shared.utils.GenderUnknown;
import shared.utils.Utils;
import studio.styx.erisbot.core.ResponderInterface;
import studio.styx.erisbot.generated.enums.Gender;
import studio.styx.erisbot.generated.enums.Transactionstatus;
import studio.styx.erisbot.generated.tables.records.TransactionRecord;
import studio.styx.erisbot.generated.tables.records.UserRecord;
import studio.styx.erisbot.generated.tables.references.TablesKt;
import studio.styx.schemaEXtended.core.schemas.BooleanSchema;
import studio.styx.schemaEXtended.core.schemas.NumberSchema;
import studio.styx.schemaEXtended.core.schemas.ObjectSchema;
import studio.styx.schemaEXtended.core.schemas.StringSchema;
import translates.TranslatesObjects;
import translates.interactions.economy.ExpectedUserTransactionInteraction;
import translates.interactions.economy.TransactionTransferInteractionInterface;
import utils.ComponentBuilder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
public class AcceptTransferInteraction implements ResponderInterface {

    @Override
    public String getCustomId() {
        return "transfer/accept/:transactionId/:userId/:userAccepted/:targetId/:targetAccepted";
    }

    @Autowired
    private DSLContext dsl;

    @Override
    public void execute(ButtonInteractionEvent event) {
        String customId = event.getCustomId();
        String[] parts = customId.split("/");

        var schema = new ObjectSchema()
                .addProperty("transactionId", new NumberSchema()
                        .integer()
                        .coerce())
                .addProperty("authorId", new StringSchema())
                .addProperty("authorAccepted", new BooleanSchema().coerce())
                .addProperty("targetId", new StringSchema())
                .addProperty("targetAccepted", new BooleanSchema().coerce());

        var result = schema.parseOrThrow(Map.of(
                "transactionId", parts[1],
                "authorId", parts[2],
                "authorAccepted", parts[3],
                "targetId", parts[4],
                "targetAccepted", parts[5]
        ));

        Integer transactionId = result.get("transactionId");
        String authorId =  result.get("authorId");
        Boolean authorAccepted = result.get("authorAccepted");
        String targetId = result.get("targetId");
        Boolean targetAccepted = result.get("targetAccepted");

        TransactionTransferInteractionInterface t = TranslatesObjects.getTransactionInteraction(event.getUserLocale().getLocale());

        // Verifica se o usuário clicou é um dos envolvidos
        if (!event.getUser().getId().equals(authorId) && !event.getUser().getId().equals(targetId)) {
            ComponentBuilder.ContainerBuilder.create()
                    .withColor(Colors.DANGER)
                    .setEphemeral(true)
                    .addText(t.userNotInTransaction())
                    .reply(event);
            return;
        }

        boolean newAuthorAccepted = authorId.equals(event.getUser().getId()) ? !authorAccepted : authorAccepted;
        boolean newTargetAccepted = targetId.equals(event.getUser().getId()) ? !targetAccepted : targetAccepted;

        // Só processa se ambos aceitaram
        if (newAuthorAccepted && newTargetAccepted) {
            event.deferEdit().queue(hook -> {
                processCompleteTransaction(hook, transactionId, authorId, targetId, t);
            });
        } else {
            processPartialAcceptance(event, transactionId, authorId, targetId, newAuthorAccepted, newTargetAccepted, t);
        }
    }

    private void processCompleteTransaction(InteractionHook hook, int transactionId, String authorId, String targetId, TransactionTransferInteractionInterface t) {
        dsl.transaction(config -> {
            DSLContext tx = config.dsl();

            hook.editOriginalComponents(
                    ComponentBuilder.ContainerBuilder.create()
                            .withColor(Colors.WARNING)
                            .addText(t.processing())
                            .build()
            ).useComponentsV2().queue();

            // 1. Busca transação
            TransactionRecord transaction = getTransaction(tx, transactionId);

            if (transaction == null) {
                sendErrorEdit(hook, t.transactionNotFound());
                return;
            }

            if (transaction.getStatus() == Transactionstatus.EXPIRED) {
                sendErrorEdit(hook, t.transactionExpired());
                return;
            }

            if (transaction.getStatus() != Transactionstatus.PENDING) {
                sendErrorEdit(hook, t.transactionAlreadyAccepted());
                return;
            }

            // 2. Busca dados dos dois usuários de forma assíncrona
            hook.getJDA().retrieveUserById(authorId).queue(discordAuthor -> {
                hook.getJDA().retrieveUserById(targetId).queue(discordTarget -> {
                    processUsersComplete(hook, tx, transaction, authorId, targetId, discordAuthor, discordTarget, t);
                });
            });
        });
    }

    private void processUsersComplete(InteractionHook hook, DSLContext tx, TransactionRecord transaction,
                                      String authorId, String targetId, User discordAuthor, User discordTarget,
                                      TransactionTransferInteractionInterface t) {

        UserRecord authorData = DatabaseUtils.getOrCreateUser(tx, authorId);
        UserRecord targetData = DatabaseUtils.getOrCreateUser(tx, targetId);

        BigDecimal amount = BigDecimal.valueOf(transaction.getAmount());

        // 3. Verifica saldo
        if (authorData.getMoney().compareTo(amount) < 0) {
            hook.editOriginalComponents(
                    ComponentBuilder.ContainerBuilder.create()
                            .withColor(Colors.DANGER)
                            .addText(t.insufficientFunds(getUserGender(discordAuthor.getGlobalName(), authorData.getGender())))
                            .build()
            ).useComponentsV2().queue();
            return;
        }

        // 4. Executa as 3 operações em sequência (mas dentro da mesma transação = atômico)
        tx.update(TablesKt.getTRANSACTION())
                .set(TablesKt.getTRANSACTION().getSTATUS(), Transactionstatus.APPROVED)
                .where(TablesKt.getTRANSACTION().getID().eq(transaction.getId()))
                .execute();

        // Debitar do autor e retornar novo registro
        UserRecord updatedAuthor = tx.update(TablesKt.getUSER())
                .set(TablesKt.getUSER().getMONEY(), TablesKt.getUSER().getMONEY().minus(amount))
                .where(TablesKt.getUSER().getID().eq(authorId))
                .returning(TablesKt.getUSER().getMONEY(), TablesKt.getUSER().getGENDER())
                .fetchOne();

        // Creditar no alvo e retornar novo registro
        UserRecord updatedTarget = tx.update(TablesKt.getUSER())
                .set(TablesKt.getUSER().getMONEY(), TablesKt.getUSER().getMONEY().plus(amount))
                .where(TablesKt.getUSER().getID().eq(targetId))
                .returning(TablesKt.getUSER().getMONEY(), TablesKt.getUSER().getGENDER())
                .fetchOne();


        // 5. Monta mensagem de sucesso com dados atualizados
        ExpectedUserTransactionInteraction expectedAuthor = new ExpectedUserTransactionInteraction(
                discordAuthor.getGlobalName(),
                getUserGender(discordAuthor.getGlobalName(), updatedAuthor.getGender()),
                authorId
        );

        ExpectedUserTransactionInteraction expectedTarget = new ExpectedUserTransactionInteraction(
                discordTarget.getGlobalName(),
                getUserGender(discordTarget.getGlobalName(), updatedTarget.getGender()),
                targetId
        );

        hook.editOriginalComponents(
                ComponentBuilder.ContainerBuilder.create()
                        .withColor(Colors.SUCCESS)
                        .addText(t.message(
                                expectedAuthor,
                                expectedTarget,
                                amount.doubleValue()
                        ))
                        .build()
        ).useComponentsV2().queue();


        LogManage.CreateLog.create()
                .setMessage(t.log(expectedAuthor, expectedTarget, amount.doubleValue()))
                .setLevel(amount.doubleValue() > 1000 ? 4 : 3)
                .setUserId(authorId)
                .setTags(List.of("transfer", "economy", "target:" + targetId))
                .insert(tx);
    }

    private void processPartialAcceptance(ButtonInteractionEvent event, int transactionId, String authorId, String targetId,
                                          boolean newAuthorAccepted, boolean newTargetAccepted,
                                          TransactionTransferInteractionInterface t) {
        var tc = TranslatesObjects.getTransferCommand(event.getUserLocale().getLocale());

        // Busca usuários de forma assíncrona
        event.getJDA().retrieveUserById(authorId).queue(discordAuthor -> {
            event.getJDA().retrieveUserById(targetId).queue(discordTarget -> {
                processUsersPartial(event, transactionId, discordAuthor, discordTarget, newAuthorAccepted, newTargetAccepted, t, tc);
            });
        });
    }

    private void processUsersPartial(ButtonInteractionEvent event, int transactionId, User discordAuthor, User discordTarget,
                                     boolean newAuthorAccepted, boolean newTargetAccepted,
                                     TransactionTransferInteractionInterface t, Object tc) {
        dsl.transaction(config -> {
            DSLContext tx = config.dsl();

            TransactionRecord transaction = getTransaction(tx, transactionId);
            UserRecord authorData = DatabaseUtils.getOrCreateUser(tx, discordAuthor.getId());
            UserRecord targetData = DatabaseUtils.getOrCreateUser(tx, discordTarget.getId());

            if (transaction == null) {
                event.editComponents(
                        ComponentBuilder.ContainerBuilder.create()
                                .withColor(Colors.DANGER)
                                .addText(t.transactionNotFound())
                                .build()
                ).queue();
                return;
            }

            if (transaction.getStatus() == Transactionstatus.EXPIRED) {
                event.editComponents(
                        ComponentBuilder.ContainerBuilder.create()
                                .withColor(Colors.DANGER)
                                .addText(t.transactionExpired())
                                .build()
                ).queue();
                return;
            }

            if (transaction.getStatus() != Transactionstatus.PENDING) {
                event.editComponents(
                        ComponentBuilder.ContainerBuilder.create()
                                .withColor(Colors.DANGER)
                                .addText(t.transactionAlreadyAccepted())
                                .build()
                ).queue();
                return;
            }

            Button confirmButton = Button.success(
                    String.format("transfer/accept/%s/%s/%s/%s/%s", transaction.getId(), discordAuthor.getId(),
                            newAuthorAccepted ? "1" : "0", discordTarget.getId(), newTargetAccepted ? "1" : "0"),
                    ((translates.commands.economy.general.TransferTranslateInterface) tc).buttonLabel(newAuthorAccepted || newTargetAccepted)
            );

            var container = ComponentBuilder.ContainerBuilder.create()
                    .withColor(Colors.SUCCESS)
                    .addText(((translates.commands.economy.general.TransferTranslateInterface) tc).message(
                            new translates.commands.economy.general.ExpectedUser(
                                    discordAuthor.getGlobalName(),
                                    getUserGender(discordAuthor.getGlobalName(), authorData.getGender()),
                                    discordAuthor.getId()
                            ),
                            new translates.commands.economy.general.ExpectedUser(
                                    discordTarget.getName(),
                                    getUserGender(discordTarget.getGlobalName(), targetData.getGender()),
                                    discordTarget.getId()
                            ),
                            transaction.getAmount()
                    ))
                    .addRow(ActionRow.of(confirmButton))
                    .build();

            event.editComponents(container).useComponentsV2().queue();
        });
    }

    private void sendErrorEdit(InteractionHook hook, String message) {
        hook.editOriginalComponents(
                ComponentBuilder.ContainerBuilder.create()
                        .withColor(Colors.DANGER)
                        .addText(message)
                        .build()
        ).useComponentsV2().queue();
    }

    private TransactionRecord getTransaction(DSLContext tx, int transactionId) {
        return tx.selectFrom(TablesKt.getTRANSACTION())
                .where(TablesKt.getTRANSACTION().getID().eq(transactionId))
                .fetchOne();
    }

    private GenderUnknown getUserGender(String name, Gender gender) {
        if (gender != null) {
            return gender == Gender.MALE ? GenderUnknown.MALE : GenderUnknown.FEMALE;
        }
        return Utils.getNameGender(name);
    }
}