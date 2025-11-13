package studio.styx.erisbot.features.commands.economy;

import database.utils.DatabaseUtils;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
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
import utils.ComponentBuilder;

import java.math.BigDecimal;

@Component
public class Balance implements CommandInterface {

    @Autowired
    private DSLContext dsl;

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping userOption = event.getOption("user");
        User targetUser = userOption != null ? userOption.getAsUser() : event.getUser();

        if (targetUser.isBot()) {
            ComponentBuilder.ContainerBuilder.create()
                    .addText("Você não pode verificar o saldo de um bot.")
                    .withColor(Colors.DANGER)
                    .setEphemeral(true)
                    .reply(event);
            return;
        }

        String userId = targetUser.getId();
        UserRecord userRecord = DatabaseUtils.getOrCreateUser(dsl, userId);
        BigDecimal money = userRecord.getMoney() != null ? userRecord.getMoney() : BigDecimal.ZERO;

        String replyMessage = userOption == null
                ? String.format("Seu saldo é de **%s** stx.", Utils.formatMoney(money))
                : String.format("O saldo de %s é de **%s** stx.", targetUser.getAsMention(), Utils.formatMoney(money));

        ComponentBuilder.ContainerBuilder.create()
                .addText(replyMessage)
                .withColor(Colors.FUCHSIA)
                .disableMentions()
                .reply(event);
    }

    @Override
    public SlashCommandData getSlashCommandData() {
        return Commands.slash("balance", "Verifica o seu saldo ou o de outro usuário")
                .addOption(OptionType.USER, "user", "O usuário para verificar o saldo", false);
    }
}