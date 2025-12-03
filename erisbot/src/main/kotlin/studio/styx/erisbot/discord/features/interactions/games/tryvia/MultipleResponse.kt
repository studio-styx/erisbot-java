package studio.styx.erisbot.discord.features.interactions.games.tryvia

import games.tryvia.core.TryviaGame
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import org.springframework.stereotype.Component
import shared.Cache
import shared.Colors
import shared.utils.CustomIdHelper
import shared.utils.Icon
import studio.styx.erisbot.core.interfaces.ResponderInterface
import utils.ComponentBuilder

@Component
class MultipleResponse : ResponderInterface {
    override val customId: String = "tryvia/game/multiple/:isCorrect/:letter"

    override suspend fun execute(event: ButtonInteractionEvent) {
        val game = Cache.get<TryviaGame>("tryvia:game:${event.channel.id}") ?: return run {
            ComponentBuilder.ContainerBuilder.create()
                .withColor(Colors.DANGER)
                .addText("O jogo de trivia nesse canal foi fechado!")
                .editOriginal(event)
        }
        val params = CustomIdHelper(customId, event.customId)

        val isCorrect = params.get("isCorrect") == "true"

        game.setActualQuestionResponded(true)

        if (isCorrect) {
            ComponentBuilder.ContainerBuilder.create()
                .withColor(Colors.SUCCESS)
                .addText("${Icon.static.get("Eris_happy")} | Parabéns você acertou a pergunta!")
                .setEphemeral(true)
                .reply(event)
            game.sendSuccefullyAnsweredMessage(event.channel, event.user.id)
        } else {
            ComponentBuilder.ContainerBuilder.create()
                .withColor(Colors.DANGER)
                .addText("${Icon.static.get("Eris_cry")} | Infelizmente essa não era a resposta para a pergunta")
                .setEphemeral(true)
                .reply(event)
        }
    }
}