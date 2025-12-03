package studio.styx.erisbot.discord.features.interactions.games.fish

import dev.minn.jda.ktx.coroutines.await
import games.fish.fishingMenu
import games.fish.setFishTimeout
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import org.springframework.stereotype.Component
import shared.utils.CustomIdHelper
import shared.utils.Utils
import studio.styx.erisbot.core.exceptions.InteractionUsedByUnauthorizedUserException
import studio.styx.erisbot.core.interfaces.ResponderInterface

@Component
class FishingStart : ResponderInterface {
    override val customId = "fishing/start/:userId/:fishingRodId"

    override suspend fun execute(event: ButtonInteractionEvent) {
        val params = CustomIdHelper(customId, event.customId)

        val userId = params.get("userId")!!
        val fishingRodId = params.getAsInt("fishingRodId")!!

        if (event.user.id != userId) throw InteractionUsedByUnauthorizedUserException(userId)

        event.editComponents(fishingMenu(userId, fishingRodId)).useComponentsV2().await()
        setFishTimeout(event, 1, Utils.getRandomLong(0, 10000), userId)
    }
}
