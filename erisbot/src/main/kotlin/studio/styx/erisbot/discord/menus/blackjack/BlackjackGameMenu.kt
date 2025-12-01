package studio.styx.erisbot.discord.menus.blackjack

import games.blackjack.core.dtos.BlackjackCardObject
import games.blackjack.core.singlePlayer.BlackjackEndGameResultType
import games.blackjack.core.singlePlayer.BlackjackGame
import net.dv8tion.jda.api.components.MessageTopLevelComponent
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.interactions.DiscordLocale
import shared.Colors
import translates.TranslatesObjects.getBlackjack
import translates.commands.economy.cassino.blackjack.BlackjackTranslateInterface
import translates.commands.economy.cassino.blackjack.Card
import utils.ComponentBuilder.ContainerBuilder.Companion.create
import java.util.*
import java.util.List

class BlackjackGameMenu {
    private var userId: String? = null
    private var amount: Double? = null
    private var t = getBlackjack("ptbr")
    private var action: BlackjackAction? = null
    private var game: BlackjackGame? = null
    private var disableButtons = false
    private var wins: BlackjackEndGameResultType? = null
    private var comentary: String? = null

    fun setUserId(userId: String?): BlackjackGameMenu {
        this.userId = userId
        return this
    }

    fun setAmount(amount: Double): BlackjackGameMenu {
        this.amount = amount
        return this
    }

    fun setTraduction(t: BlackjackTranslateInterface): BlackjackGameMenu {
        this.t = t
        return this
    }

    fun setAction(action: BlackjackAction): BlackjackGameMenu {
        this.action = action
        return this
    }

    fun setGame(game: BlackjackGame): BlackjackGameMenu {
        this.game = game
        return this
    }

    fun setDisableButtons(disableButtons: Boolean): BlackjackGameMenu {
        this.disableButtons = disableButtons
        return this
    }

    fun setWins(wins: BlackjackEndGameResultType?): BlackjackGameMenu {
        this.wins = wins
        return this
    }

    fun setComentary(comentary: String?): BlackjackGameMenu {
        this.comentary = comentary
        return this
    }

    fun blackjackGameMenu(locale: DiscordLocale?): MutableList<MessageTopLevelComponent?> {
        if (this.wins != null) {
            val multiplier = if (game!!.difficulty <= 1) 1.5 else game!!.difficulty * 1.5

            val container = create()
                .addText(t.title(game!!.difficulty))
                .addDivider(false)
                .addText(
                    t.erisHand(
                        game!!.erisCards.stream()
                            .map<Card?> { card: BlackjackCardObject? -> Card(card!!.card, card.number) }.toList(),
                        game!!.calculateHandValue(game!!.erisCards),
                        game!!.difficulty,
                        false
                    )
                )
                .addDivider(false)
                .addText(
                    t.userHand(
                        game!!.userCards.stream()
                            .map<Card?> { card: BlackjackCardObject? -> Card(card!!.card, card.number) }.toList(),
                        game!!.calculateHandValue(game!!.userCards)
                    )
                )

            if (game!!.difficulty != 0) {
                container.addDivider(false)
                    .addText(t.humor(game!!.humor.name.lowercase(Locale.getDefault())))
                if (this.comentary != null) {
                    container.addText(comentary)
                }
            }

            container.addText(t.winsMessage(wins!!.name.lowercase(Locale.getDefault()), amount!!, multiplier))
                .withColor(
                    if (wins == BlackjackEndGameResultType.BOT)
                        Colors.DANGER
                    else
                        if (wins == BlackjackEndGameResultType.PLAYER)
                            Colors.SUCCESS
                        else
                            Colors.SECONDARY
                )

            return List.of<MessageTopLevelComponent?>(container.build())
        }

        val container = create()
            .addText(t.title(game!!.difficulty))
            .addDivider(false)
            .addText(
                t.erisHand(
                    game!!.erisCards.stream()
                        .map<Card?> { card: BlackjackCardObject? -> Card(card!!.card, card.number) }
                        .toList(),
                    game!!.calculateHandValue(game!!.erisCards),
                    game!!.difficulty,
                    true
                ))
            .addDivider(false)
            .addText(
                t.userHand(
                    game!!.userCards.stream()
                        .map<Card?> { card: BlackjackCardObject? -> Card(card!!.card, card.number) }
                        .toList(),
                    game!!.calculateHandValue(game!!.userCards)
                ))

        if (game!!.difficulty != 0) {
            container.addDivider(false)
                .addText(t.humor(game!!.humor.name.lowercase(Locale.getDefault())))
            if (this.comentary != null) {
                container.addText(comentary)
            }
        }

        container.addText(t.erisAction(this.action!!.name.lowercase(Locale.getDefault())))
            .addDivider(false)
            .addRow(
                ActionRow.of(
                    Button.primary("blackjack/game/hit/" + userId, t.buttons.hit)
                        .withDisabled(this.disableButtons),
                    Button.secondary("blackjack/game/pass/" + userId, t.buttons.pass)
                        .withDisabled(this.disableButtons),
                    Button.danger("blackjack/game/stand/" + userId, t.buttons.stand)
                        .withDisabled(this.disableButtons || game!!.turnCount <= 5)
                )
            )
            .withColor(Colors.FUCHSIA)

        return mutableListOf<MessageTopLevelComponent?>(container.build())
    }
}
