package studio.styx.erisbot.core.dtos.blackjack

import games.blackjack.core.multiPlayer.BlackjackMultiplayerGame
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent

data class BlackjackMultiplayerGameCache(
    val game: BlackjackMultiplayerGame,
    val userEvent: ButtonInteractionEvent?,
    val targetEvent: ButtonInteractionEvent?
)