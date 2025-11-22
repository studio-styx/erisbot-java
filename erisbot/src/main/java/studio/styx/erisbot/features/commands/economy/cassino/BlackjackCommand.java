package studio.styx.erisbot.features.commands.economy.cassino;

import database.utils.DatabaseUtils;
import games.blackjack.core.singlePlayer.BlackjackGame;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import shared.Cache;
import shared.Colors;
import studio.styx.erisbot.core.CommandInterface;
import studio.styx.erisbot.generated.tables.records.UserRecord;
import studio.styx.erisbot.menus.economy.cassino.blackjack.BlackjackPreStart;
import translates.TranslatesObjects;
import utils.ComponentBuilder;

@Component
public class BlackjackCommand implements CommandInterface {
    @Autowired
    private DSLContext dsl;

    @Override
    public SlashCommandData getSlashCommandData() {
        OptionData amount = new OptionData(OptionType.NUMBER, "amount", "valor a apostar")
                .setMinValue(50);

        return Commands.slash("blackjack", "jogue uma partida de blackjack")
                .addOptions(amount);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        BlackjackGame game = Cache.get("blackjack:game:singlePlayer:" + event.getUser().getId());

        if (game != null) {
            ComponentBuilder.ContainerBuilder.create()
                    .withColor(Colors.DANGER)
                    .addText("You are already in a game, press the red button to delete the games")
                    .addRow(ActionRow.of(
                            Button.danger("blackjack/delete/delete", "Delete")
                    ))
                    .setEphemeral(true)
                    .reply(event);
            return;
        }

        var t = TranslatesObjects.getBlackjackPreStart(event.getUserLocale().getLocale());

        double amount = event.getOption("amount").getAsDouble();
        UserRecord user = DatabaseUtils.getOrCreateUser(dsl, event.getUser().getId());
        if (user.getMoney().doubleValue() < amount) {
            amount = user.getMoney().doubleValue();
        }
        if (amount < 50) {
            ComponentBuilder.ContainerBuilder.create()
                    .withColor(Colors.DANGER)
                    .addText(t.notEnoughMoney())
                    .setEphemeral(true)
                    .reply(event);
            return;
        }

        BlackjackPreStart menu = new BlackjackPreStart();

        Integer games = Cache.get("blackjack:games:amount:" + event.getUser().getId());

        event.replyComponents(menu.blackjackContainer(
                event.getUser().getId(), t,
                games == null ? 0 : games, amount
        )).useComponentsV2().queue();
    }
}
