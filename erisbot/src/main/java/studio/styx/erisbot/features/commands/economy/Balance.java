package studio.styx.erisbot.features.commands.economy;

import database.utils.DatabaseUtils;
import database.utils.LogManage;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import shared.Colors;
import shared.utils.Utils;
import studio.styx.erisbot.core.CommandInterface;
import studio.styx.erisbot.generated.tables.records.UserRecord;
import translates.TranslatesObjects;
import utils.ComponentBuilder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class Balance implements CommandInterface {

    @Autowired
    private DSLContext dsl;

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping userOption = event.getOption("user");
        User targetUser = userOption != null ? userOption.getAsUser() : event.getUser();

        var t = TranslatesObjects.getBalance(event.getUserLocale().getLocale());

        if (targetUser.getId().equals(event.getJDA().getSelfUser().getId())) {
            ComponentBuilder.ContainerBuilder.create()
                    .addText(t.getErisMoney())
                    .withColor(Colors.DANGER);
        }

        if (targetUser.isBot()) {
            ComponentBuilder.ContainerBuilder.create()
                    .addText(t.getBotMoney())
                    .withColor(Colors.DANGER)
                    .setEphemeral(true)
                    .reply(event);
            return;
        }

        String userId = targetUser.getId();
        UserRecord userRecord = DatabaseUtils.getOrCreateUser(dsl, userId);
        BigDecimal money = userRecord.getMoney() != null ? userRecord.getMoney() : BigDecimal.ZERO;

        List<String> replyMessage = t.message(money.doubleValue(), userId);

        ComponentBuilder.ContainerBuilder.create()
                .addText(Utils.getRandomListValue(replyMessage))
                .withColor(Colors.FUCHSIA)
                .disableMentions()
                .reply(event);

        LogManage.CreateLog.create()
                .setMessage(String.format(t.log(userId, targetUser.getId())))
                .setLevel(1)
                .setTags(List.of("balance", "economy", "view", "target:" + targetUser.getId()))
                .insert(dsl);
    }

    @Override
    public SlashCommandData getSlashCommandData() {
        return Commands.slash("balance", "💳 ✦ Check your balance or another user's balance")
                .addOption(OptionType.USER, "user", "👤 ✦ User to check balance", false)
                .setNameLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "usuário")
                .setDescriptionLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "👤 ✦ O usuário para verificar o saldo")
                .setNameLocalization(DiscordLocale.SPANISH, "usuario")
                .setDescriptionLocalization(DiscordLocale.SPANISH, "👤 ✦ El usuario para verificar el saldo")
                .setNameLocalization(DiscordLocale.SPANISH_LATAM, "usuario")
                .setDescriptionLocalization(DiscordLocale.SPANISH_LATAM, "👤 ✦ El usuario para verificar el saldo")
                .setNameLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "saldo")
                .setDescriptionLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "💳 ✦ Verifica o seu saldo ou o de outro usuário")
                .setNameLocalization(DiscordLocale.SPANISH, "saldo")
                .setDescriptionLocalization(DiscordLocale.SPANISH, "💳 ✦ Verifica tu saldo o el de otro usuario")
                .setNameLocalization(DiscordLocale.SPANISH_LATAM, "saldo")
                .setDescriptionLocalization(DiscordLocale.SPANISH_LATAM, "💳 ✦ Verifica tu saldo o el de otro usuario")
                .setNameLocalization(DiscordLocale.ENGLISH_US, "balance")
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US, "💳 ✦ Check your balance or another user's balance");
    }
}