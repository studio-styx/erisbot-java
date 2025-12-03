package studio.styx.erisbot.discord.features.listeners

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.springframework.stereotype.Component
import studio.styx.erisbot.discord.features.events.message.trivia.WriteResponse

@Component
class MessageListener : ListenerAdapter() {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onMessageReceived(event: MessageReceivedEvent) {
        scope.launch {
            WriteResponse().execute(event) // resposta de texto trivia
        }
    }
}