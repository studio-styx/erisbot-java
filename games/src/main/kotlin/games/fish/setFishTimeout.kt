package games.fish

import dev.minn.jda.ktx.coroutines.await
import kotlinx.coroutines.delay
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import shared.utils.Utils

suspend fun setFishTimeout(event: ButtonInteractionEvent, round: Int, timeToEnd: Long, userId: String) {
    delay(timeToEnd)

    val channel = event.channel
    val message = channel.retrieveMessageById(event.messageId).await()

    val components = message.components[0]
    val textDisplay = components.asContainer().components[0].asTextDisplay()
    val rodId = textDisplay.uniqueId
    val messageRound = textDisplay.content.split("| ")[1].toInt()

    if (messageRound != round) return

    val randomButton = Utils.getRandomInt(1, 5)

    message.editMessageComponents(fishingMenu(userId, rodId, round, randomButton))
}