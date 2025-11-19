package studio.styx.erisbot.features.commands.economy;

import database.utils.DatabaseUtils;
import database.utils.LogManage;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import shared.Colors;
import shared.utils.GenderUnknown;
import shared.utils.Utils;
import studio.styx.erisbot.core.CommandInterface;
import studio.styx.erisbot.generated.enums.Gender;
import studio.styx.erisbot.generated.enums.Transactionstatus;
import studio.styx.erisbot.generated.tables.records.TransactionRecord;
import studio.styx.erisbot.generated.tables.records.UserRecord;
import studio.styx.erisbot.generated.tables.references.TablesKt;
import translates.TranslatesObjects;
import translates.commands.economy.general.ExpectedUser;
import translates.commands.economy.general.TransferTranslateInterface;
import utils.ComponentBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Component
public class Transfer implements CommandInterface {

    @Autowired
    private DSLContext dsl;

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        User user = event.getUser();
        String userId = user.getId();
        Double amountDouble = event.getOption("amount").getAsDouble();
        User target = event.getOption("user") != null ? event.getOption("user").getAsUser() : null;

        TransferTranslateInterface t = TranslatesObjects.getTransferCommand(event.getUserLocale().getLocale());

        BigDecimal amount = BigDecimal.valueOf(amountDouble);

        dsl.transaction(config -> {
            DSLContext tx = config.dsl();

            // 1. Busca ambos os usuários em 1 query (otimizado)
            UserRecord userData = DatabaseUtils.getOrCreateUser(tx, userId);
            UserRecord targetData = target != null ? DatabaseUtils.getOrCreateUser(tx, target.getId()) : null;

            if (userData.getMoney().compareTo(amount) < 0) {
                ComponentBuilder.ContainerBuilder.create()
                        .addText(t.insufficientFunds())
                        .setEphemeral(true)
                        .reply(event);
                return;
            }

            // 2. Caso: múltiplos usuários
            if (target == null) {
                handleMultipleUsers(event, userData, amount, t);
                return;
            }

            // 3. Caso: transferência direta

            if (target.getId().equals(userId)) {
                ComponentBuilder.ContainerBuilder.create()
                        .addText(t.tryingToSendMoneyToOurSelf())
                        .withColor(Colors.DANGER)
                        .setEphemeral(true)
                        .reply(event);
                return;
            }

            if (target.getId().equals(event.getJDA().getSelfUser().getId())) {
                ComponentBuilder.ContainerBuilder.create()
                        .addText(t.tryingToSendMoneyToOurSelf())
                        .withColor(Colors.DANGER)
                        .setEphemeral(true)
                        .reply(event);
                return;
            }

            if (target.isBot()) {
                ComponentBuilder.ContainerBuilder.create()
                        .addText(t.tryingToSendMoneyToABot())
                        .withColor(Colors.DANGER)
                        .setEphemeral(true)
                        .reply(event);
                return;
            }

            handleDirectTransfer(event, userData, targetData, amount, user, target, t);
        });
    }

    // === MÚLTIPLOS USUÁRIOS ===
    private void handleMultipleUsers(SlashCommandInteractionEvent event, UserRecord userData, BigDecimal amount, TransferTranslateInterface t) {
        int maxUsers = Math.max(2, Math.min(25, (int) (userData.getMoney().doubleValue() / amount.doubleValue())));
        if (maxUsers < 2) {
            event.reply(t.insufficientFunds()).setEphemeral(true).queue();
            return;
        }

        EntitySelectMenu menu = EntitySelectMenu.create("manyTransfer/selectUsers/" + event.getUser().getId() + "/" + amount.toString(), EntitySelectMenu.SelectTarget.USER)
                .setPlaceholder(t.manyUsersUserSelectLabel())
                .setRequiredRange(1, maxUsers)
                .build();

        ComponentBuilder.ContainerBuilder.create()
                .addText(t.manyUsersTitle())
                .addDivider(false)
                .addText(t.manyUsersDescription(amount.doubleValue()))
                .addRow(ActionRow.of(menu))
                .setEphemeral(false)
                .withColor(Colors.FUCHSIA)
                .reply(event);
    }

    // === TRANSFERÊNCIA DIRETA ===
    private void handleDirectTransfer(SlashCommandInteractionEvent event, UserRecord userData, UserRecord targetData,
                                      BigDecimal amount, User user, User target, TransferTranslateInterface t) {

        if (userData.getMoney().compareTo(amount) < 0) {
            ComponentBuilder.ContainerBuilder.create()
                    .addText(t.insufficientFunds())
                    .setEphemeral(true)
                    .reply(event);
            return;
        }

        // Cria transação
        TransactionRecord transaction = createTransaction(
                dsl,
                user.getId(),
                target.getId(),
                amount,
                event.getChannel().getId(),
                event.getGuild().getId()
        );

        // Botão de confirmação
        Button confirmButton = Button.success(
                String.format("transfer/accept/%s/%s/%s/%s/%s", transaction.getId(), user.getId(), "0", target.getId(), "0"),
                t.buttonLabel(false)
        );

        ComponentBuilder.ContainerBuilder.create()
                .withColor(Colors.SUCCESS)
                .setEphemeral(false)
                .addText(t.message(
                        new ExpectedUser(user.getName(), getUserGender(user.getGlobalName(), userData.getGender()), user.getId()),
                        new ExpectedUser(target.getName(), getUserGender(target.getGlobalName(), targetData.getGender()), target.getId()),
                        amount.doubleValue()
                ))
                .addRow(ActionRow.of(confirmButton))
                .reply(event);

        LogManage.CreateLog.create()
                .setMessage(t.log(
                        new ExpectedUser(
                                user.getGlobalName(),
                                getUserGender(
                                        user.getGlobalName(),
                                        userData.getGender()
                                ),
                                user.getId()
                        ),
                        new ExpectedUser(
                                target.getGlobalName(),
                                getUserGender(
                                        target.getGlobalName(),
                                        targetData.getGender()
                                ),
                                target.getId()
                        ),
                        amount.doubleValue())
                )
                .setLevel(4)
                .setTags(List.of("transfer", "economy", "target:" + target.getId()))
                .insert(dsl);

    }

    // === CRIA TRANSACAO ===
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

    // === GÊNERO DO USUÁRIO ===
    private GenderUnknown getUserGender(String name, Gender gender) {
        if (gender != null) {
            return gender == Gender.MALE ? GenderUnknown.MALE : GenderUnknown.FEMALE;
        }
        return Utils.getNameGender(name);
    }

    @Override
    public SlashCommandData getSlashCommandData() {
        return Commands.slash("transfer", "💸 ✦ Starts a stx transfer")
                .addOptions(
                        new OptionData(OptionType.NUMBER, "amount", "💰 ✦ Amount of stx to transfer", true)
                                .setMinValue(20)
                                .setNameLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "quantidade")
                                .setDescriptionLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "💰 ✦ Quantidade de stx para transferir")
                                .setNameLocalization(DiscordLocale.SPANISH, "cantidad")
                                .setDescriptionLocalization(DiscordLocale.SPANISH, "💰 ✦ Cantidad de stx para transferir")
                                .setNameLocalization(DiscordLocale.SPANISH_LATAM, "cantidad")
                                .setDescriptionLocalization(DiscordLocale.SPANISH_LATAM, "💰 ✦ Cantidad de stx para transferir"),
                        new OptionData(OptionType.USER, "user", "👤 ✦ User to transfer to (optional)", false)
                                .setNameLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "usuário")
                                .setDescriptionLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "👤 ✦ Usuário para transferir (opcional)")
                                .setNameLocalization(DiscordLocale.SPANISH, "usuario")
                                .setDescriptionLocalization(DiscordLocale.SPANISH, "👤 ✦ Usuario para transferir (opcional)")
                                .setNameLocalization(DiscordLocale.SPANISH_LATAM, "usuario")
                                .setDescriptionLocalization(DiscordLocale.SPANISH_LATAM, "👤 ✦ Usuario para transferir (opcional)")
                )
                .setNameLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "transferir")
                .setDescriptionLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "💸 ✦ Inicia uma transferência de stx")
                .setNameLocalization(DiscordLocale.SPANISH, "transferir")
                .setDescriptionLocalization(DiscordLocale.SPANISH, "💸 ✦ Inicia una transferencia de stx")
                .setNameLocalization(DiscordLocale.SPANISH_LATAM, "transferir")
                .setDescriptionLocalization(DiscordLocale.SPANISH_LATAM, "💸 ✦ Inicia una transferencia de stx")
                .setNameLocalization(DiscordLocale.ENGLISH_US, "transfer")
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US, "💸 ✦ Starts a stx transfer");
    }
}