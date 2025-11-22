package studio.styx.erisbot.menus.economy.cassino.blackjack;

import games.blackjack.core.singlePlayer.BlackjackEndGameResultType;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import shared.Colors;
import translates.TranslatesObjects;
import translates.commands.economy.cassino.blackjack.BlackjackTranslateInterface;
import translates.commands.economy.cassino.blackjack.Card;
import utils.ComponentBuilder;

import java.util.List;

public class BlackjackGame {
    private String userId;
    private Double amount;
    private BlackjackTranslateInterface t = TranslatesObjects.getBlackjack("ptbr");
    private BlackjackAction action;
    private games.blackjack.core.singlePlayer.BlackjackGame game;
    private Boolean disableButtons = false;
    private BlackjackEndGameResultType wins;
    private String comentary;

    public BlackjackGame setUserId(String userId) {
        this.userId = userId;
        return this;
    }

    public BlackjackGame setAmount(Double amount) {
        this.amount = amount;
        return this;
    }

    public BlackjackGame setTraduction(BlackjackTranslateInterface t) {
        this.t = t;
        return this;
    }

    public BlackjackGame setAction(BlackjackAction action) {
        this.action = action;
        return this;
    }

    public BlackjackGame setGame(games.blackjack.core.singlePlayer.BlackjackGame game) {
        this.game = game;
        return this;
    }

    public BlackjackGame setDisableButtons(Boolean disableButtons) {
        this.disableButtons = disableButtons;
        return this;
    }

    public BlackjackGame setWins(BlackjackEndGameResultType wins) {
        this.wins = wins;
        return this;
    }

    public BlackjackGame setComentary(String comentary) {
        this.comentary = comentary;
        return this;
    }

    public List<MessageTopLevelComponent> blackjackGameMenu(DiscordLocale locale) {
        if (this.wins != null) {
            double multiplier = game.getDifficulty() <= 1 ? 1.5 : game.getDifficulty() * 1.5;

            ComponentBuilder.ContainerBuilder container = ComponentBuilder.ContainerBuilder.create()
                    .addText(t.title(game.getDifficulty()))
                    .addDivider(false)
                    .addText(t.erisHand(
                            game.getErisCards().stream().map(card -> new Card(card.getCard(), card.getNumber())).toList(),
                            game.calculateHandValue(game.getErisCards()),
                            game.getDifficulty(),
                            false
                    ))
                    .addDivider(false)
                    .addText(t.userHand(
                            game.getUserCards().stream().map(card -> new Card(card.getCard(), card.getNumber())).toList(),
                            game.calculateHandValue(game.getUserCards())
                    ));

            if (game.getDifficulty() != 0) {
                container.addDivider(false)
                        .addText(t.humor(game.getHumor().name().toLowerCase()));
                if (this.comentary != null) {
                    container.addText(comentary);
                }
            }

            container.addText(t.winsMessage(wins.name().toLowerCase(), amount, multiplier))
                    .withColor(wins == BlackjackEndGameResultType.BOT
                            ? Colors.DANGER
                            : wins == BlackjackEndGameResultType.PLAYER
                                ? Colors.SUCCESS
                                : Colors.SECONDARY);

            return List.of(container.build());
        }

        ComponentBuilder.ContainerBuilder container = ComponentBuilder.ContainerBuilder.create()
                .addText(t.title(game.getDifficulty()))
                .addDivider(false)
                .addText(t.erisHand(
                        game.getErisCards().stream().map(card -> new Card(card.getCard(), card.getNumber())).toList(),
                        game.calculateHandValue(game.getErisCards()),
                        game.getDifficulty(),
                        true
                ))
                .addDivider(false)
                .addText(t.userHand(
                        game.getUserCards().stream().map(card -> new Card(card.getCard(), card.getNumber())).toList(),
                        game.calculateHandValue(game.getUserCards())
                ));

        if (game.getDifficulty() != 0) {
            container.addDivider(false)
                    .addText(t.humor(game.getHumor().name().toLowerCase()));
            if (this.comentary != null) {
                container.addText(comentary);
            }
        }

        container.addText(t.erisAction(this.action.name().toLowerCase()))
                .addDivider(false)
                .addRow(ActionRow.of(
                        Button.primary("blackjack/game/hit/" + userId, t.getButtons().getHit())
                                .withDisabled(this.disableButtons),
                        Button.secondary("blackjack/game/pass/" + userId, t.getButtons().getPass())
                                .withDisabled(this.disableButtons),
                        Button.danger("blackjack/game/stand/" + userId, t.getButtons().getStand())
                                .withDisabled(this.disableButtons || game.getTurnCount() <= 5)
                ))
                .withColor(Colors.FUCHSIA);

        return List.of(container.build());
    }
}
