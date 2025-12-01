package studio.styx.erisbot.discord.menus.blackjack

import games.blackjack.core.multiPlayer.BlackjackMultiplayerGame
import games.blackjack.core.multiPlayer.BlackjackMultiplayerPlayers
import net.dv8tion.jda.api.components.MessageTopLevelComponent
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import shared.Colors
import translates.commands.economy.cassino.blackjack.Card
import utils.ComponentBuilder

fun blackjackMultiplayerMenu(
    game: BlackjackMultiplayerGame,
    endGameReason: String?
): MutableList<MessageTopLevelComponent?> {
    val t = game.t

    val userCards = game.userCards.map { c -> Card(c.card, c.number) }
    val targetCards = game.targetCards.map { c -> Card(c.card, c.number) }

    return mutableListOf(ComponentBuilder.ContainerBuilder.create()
        .withColor(Colors.DANGER)
        .addText(t.multiplayerTitle(game.userId, game.targetId, game.amountAposted))
        .addDivider()
        .addText(t.multiplayerUserHand(game.userId, userCards, game.calculateHandValue(game.userCards), endGameReason != null))
        .addDivider()
        .addText(t.multiplayerUserHand(game.targetId, targetCards, game.calculateHandValue(game.targetCards), endGameReason != null))
        .addDivider()
        .addText(t.multiplayerTurn(endGameReason, game.turn.toString().lowercase(), game.userId, game.targetId))
        .addRow(ActionRow.of(
            Button.primary(
                "blackjackMultiplayer/game/hit/${if(game.turn == BlackjackMultiplayerPlayers.PLAYER)  game.userId else game.targetId}",
                t.multiplayerButtons.hit
            ).withDisabled(endGameReason != null),
            Button.secondary(
                "blackjackMultiplayer/game/pass/${if(game.turn == BlackjackMultiplayerPlayers.PLAYER)  game.userId else game.targetId}",
                t.multiplayerButtons.pass
            ).withDisabled(endGameReason != null),
            Button.danger(
                "blackjackMultiplayer/game/stand/${if(game.turn == BlackjackMultiplayerPlayers.PLAYER)  game.userId else game.targetId}",
                t.multiplayerButtons.stand
            ).withDisabled(
                when {
                    endGameReason != null -> true
                    game.turnCount <= 4 -> true
                    else -> false
                }
            ),
        ))
        .build()
    )
}