package studio.styx.erisbot.features.interactions.economy;

import database.utils.DatabaseUtils;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import shared.Colors;
import shared.utils.GenderUnknown;
import shared.utils.Icon;
import shared.utils.Utils;
import studio.styx.erisbot.core.ResponderInterface;
import studio.styx.erisbot.generated.enums.Gender;
import studio.styx.erisbot.generated.enums.Transactionstatus;
import studio.styx.erisbot.generated.tables.records.TransactionRecord;
import studio.styx.erisbot.generated.tables.records.UserRecord;
import studio.styx.erisbot.generated.tables.references.TablesKt;
import translates.TranslatesObjects;
import translates.commands.economy.general.ExpectedUser;
import utils.ComponentBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ManyTransfers implements ResponderInterface {
    @Autowired
    private DSLContext dsl;

    @Override
    public String getCustomId() {
        return "manyTransfer/selectUsers/:userId/:amount";
    }

    @Override
    public void execute(EntitySelectInteractionEvent event) {
        String[] parts = event.getCustomId().split("/");
        String authorId = parts[2];
        double amountPerUser = Double.parseDouble(parts[3]);

        var t = TranslatesObjects.getManyTransferInteraction(event.getUserLocale().getLocale());

        if (!authorId.equals(event.getUser().getId())) {
            event.replyComponents(
                    ComponentBuilder.ContainerBuilder.create()
                            .addText(t.userCannotUseThisButton())
                            .withColor(Colors.DANGER)
                            .build()
            ).useComponentsV2().setEphemeral(true).queue();
            return;
        }

        event.getValues().forEach(entity -> {
            System.out.println("- Classe: " + entity.getClass().getSimpleName() + ", ID: " + entity.getId());
        });

        var selectedUsers = event.getValues().stream()
                .filter(entity -> entity instanceof Member) // Filtra Member
                .map(entity -> (Member) entity) // Converte para Member
                .map(Member::getUser) // Obtém o User do Member
                .filter(user -> !user.isBot())
                .filter(user -> !user.getId().equals(authorId))
                .distinct()
                .toList();

        selectedUsers.forEach(user -> System.out.println("- " + user.getGlobalName() + " (" + user.getId() + ")"));

        if (selectedUsers.isEmpty()) {
            event.reply("Nenhum usuário válido selecionado.").setEphemeral(true).queue();
            return;
        }

        double totalAmount = amountPerUser * selectedUsers.size();

        event.deferEdit().queue(hook -> {
            dsl.transaction(config -> {
                DSLContext tx = config.dsl();

                List<String> targetIds = selectedUsers.stream()
                        .map(User::getId)
                        .collect(Collectors.toList());

                List<UserRecord> targetRecords = getOrCreateUsers(tx, targetIds);

                UserRecord authorRecord = DatabaseUtils.getOrCreateUser(tx, authorId);
                if (authorRecord.getMoney().doubleValue() < totalAmount) {
                    System.out.println("Não tem dinheiro suficiente");
                    hook.sendMessageComponents(
                            ComponentBuilder.ContainerBuilder.create()
                                    .withColor(Colors.DANGER)
                                    .addText(t.insufficientFunds())
                                    .build()
                    ).useComponentsV2().setEphemeral(true).queue();
                    return;
                }

                LocalDateTime now = LocalDateTime.now();
                LocalDateTime expiresAt = now.plusHours(24);
                String channelId = event.getChannel().getId();
                String guildId = event.getGuild().getId();

                var tc = TranslatesObjects.getTransferCommand(event.getUserLocale().getLocale());

                hook.editOriginalComponents(TextDisplay.of(
                        Icon.INSTANCE.getAnimated().get("waiting_white") != null
                            ? Icon.INSTANCE.getAnimated().get("waiting_white")
                                : "Aguarde..."
                )).useComponentsV2().queue();

                var insert = tx.insertInto(TablesKt.getTRANSACTION(),
                        TablesKt.getTRANSACTION().getUSERID(),
                        TablesKt.getTRANSACTION().getTARGETID(),
                        TablesKt.getTRANSACTION().getAMOUNT(),
                        TablesKt.getTRANSACTION().getCHANNELID(),
                        TablesKt.getTRANSACTION().getGUILDID(),
                        TablesKt.getTRANSACTION().getSTATUS(),
                        TablesKt.getTRANSACTION().getCREATEDAT(),
                        TablesKt.getTRANSACTION().getUPDATEDAT(),
                        TablesKt.getTRANSACTION().getEXPIRESAT()
                );

                for (String targetId : targetIds) {
                    insert.values(
                            authorId,
                            targetId,
                            amountPerUser,
                            channelId,
                            guildId,
                            Transactionstatus.PENDING,
                            now,
                            now,
                            expiresAt
                    );
                }

                List<TransactionRecord> transactions = insert
                        .onDuplicateKeyIgnore()
                        .returning(TablesKt.getTRANSACTION().getID(), TablesKt.getTRANSACTION().getTARGETID())
                        .fetch();

                var transactionMap = transactions.stream()
                        .collect(Collectors.toMap(
                                rec -> rec.getTargetid(),
                                TransactionRecord::getId
                        ));

                // 6. Monta ExpectedUser para todos
                ExpectedUser author = new ExpectedUser(
                        event.getUser().getGlobalName(),
                        getUserGender(event.getUser().getGlobalName(), authorRecord.getGender()),
                        authorId
                );

                var messages = selectedUsers.stream()
                        .map(user -> {
                            String targetId = user.getId();
                            ExpectedUser target = new ExpectedUser(
                                    user.getGlobalName(),
                                    getUserGender(user.getGlobalName(), targetRecords.get(targetIds.indexOf(targetId)).getGender()),
                                    targetId
                            );

                            Integer transactionId = transactionMap.get(targetId);
                            String customId = transactionId != null
                                    ? String.format("transfer/accept/%d/%s/1/%s/0", transactionId, authorId, targetId)
                                    : null;

                            return ComponentBuilder.ContainerBuilder.create()
                                    .withColor(Colors.SUCCESS)
                                    .addText(tc.message(author, target, amountPerUser))
                                    .addRow(ActionRow.of(
                                            Button.success(customId, tc.buttonLabel(true))
                                                    .withEmoji(Emoji.fromUnicode("U+1F4B0"))
                                    ))
                                    .build();
                        })
                        .collect(Collectors.toList());

                // 7. Envia todas as mensagens
                var channel = event.getChannel();

                for (var message : messages) {
                    channel.sendMessageComponents(message).useComponentsV2().queue();
                }

                hook.editOriginalComponents(
                        ComponentBuilder.ContainerBuilder.create()
                                .withColor(Colors.SUCCESS)
                                .addText("Transações iniciadas!")
                                .build()
                ).useComponentsV2().setAllowedMentions(EnumSet.noneOf(Message.MentionType.class)).queue();
            });
        });
    }

    private static List<UserRecord> getOrCreateUsers(DSLContext tx, List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) return List.of();

        // Busca usuários existentes
        var existing = tx.selectFrom(TablesKt.getUSER())
                .where(TablesKt.getUSER().getID().in(userIds))
                .fetch();

        var existingIds = existing.stream()
                .map(UserRecord::getId)
                .collect(Collectors.toSet());

        // Cria usuários faltantes
        var missingIds = userIds.stream()
                .filter(id -> !existingIds.contains(id))
                .toList();

        if (!missingIds.isEmpty()) {
            var now = java.time.LocalDateTime.now();

            // Insere usuários faltantes
            var insert = tx.insertInto(TablesKt.getUSER())
                    .columns(
                            TablesKt.getUSER().getID(),
                            TablesKt.getUSER().getMONEY(),
                            TablesKt.getUSER().getCREATEDAT(),
                            TablesKt.getUSER().getUPDATEDAT()
                    );

            // Adiciona valores para cada ID faltante
            for (String id : missingIds) {
                insert.values(id, BigDecimal.ZERO, now, now);
            }

            insert.onDuplicateKeyIgnore().execute();
        }

        // Retorna todos os usuários (existentes + criados) na mesma ordem dos IDs solicitados
        return tx.selectFrom(TablesKt.getUSER())
                .where(TablesKt.getUSER().getID().in(userIds))
                .fetch()
                .into(UserRecord.class);
    }

    private TransactionRecord createTransaction(DSLContext tx, String userId, String targetId, BigDecimal amount,
                                                String channelId, String guildId) {
        LocalDateTime now = java.time.LocalDateTime.now();

        return tx.insertInto(TablesKt.getTRANSACTION())
                .set(TablesKt.getTRANSACTION().getUSERID(), userId)
                .set(TablesKt.getTRANSACTION().getTARGETID(), targetId)
                .set(TablesKt.getTRANSACTION().getAMOUNT(), amount.doubleValue())
                .set(TablesKt.getTRANSACTION().getCHANNELID(), channelId)
                .set(TablesKt.getTRANSACTION().getGUILDID(), guildId)
                .set(TablesKt.getTRANSACTION().getSTATUS(), Transactionstatus.PENDING)
                .set(TablesKt.getTRANSACTION().getCREATEDAT(), now)
                .set(TablesKt.getTRANSACTION().getUPDATEDAT(), now)
                .set(TablesKt.getTRANSACTION().getEXPIRESAT(), now.plusHours(24))
                .returning(TablesKt.getTRANSACTION().getID())
                .fetchOne();
    }

    private GenderUnknown getUserGender(String name, Gender gender) {
        if (gender != null) {
            return gender == Gender.MALE ? GenderUnknown.MALE : GenderUnknown.FEMALE;
        }
        return Utils.getNameGender(name);
    }
}