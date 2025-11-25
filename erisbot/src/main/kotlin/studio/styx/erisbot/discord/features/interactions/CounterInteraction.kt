package studio.styx.erisbot.discord.features.interactions

import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionHook
import org.springframework.stereotype.Component
import shared.Colors
import shared.utils.Utils.formatNumber
import studio.styx.erisbot.core.interfaces.ResponderInterface
import utils.ComponentBuilder.ContainerBuilder.Companion.create
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

@Component
class CounterInteraction : ResponderInterface {
    override fun execute(event: ButtonInteractionEvent) {
        val componentId = event.getComponentId()
        val seconds = extractSeconds(componentId)

        val counter = AtomicLong(0)
        val startTime = System.nanoTime()

        // Task de contagem (roda o tempo todo)
        val finalSeconds2 = seconds
        val countingTask = Runnable {
            while (true) {
                val elapsedMs = (System.nanoTime() - startTime) / 1000000
                if (elapsedMs >= finalSeconds2 * 1000L) break

                for (i in 0..2000000 - 1) {
                    counter.incrementAndGet()
                }
            }
        }

        // Task final (responde com o resultado)
        val finalSeconds = seconds
        val sendResult = Runnable {
            val finalCount = counter.get()
            val elapsedSeconds = (System.nanoTime() - startTime) / 1000000000.0
            val speed = finalCount / elapsedSeconds

            val message = """
        **Contagem refeita!**
                
        Tempo: **$finalSeconds segundos**
        Eu contei até: **${formatNumber(finalCount)}**
        Velocidade média: **${formatNumber(speed)} por segundo**
        
        """
            create()
                .addText(message)
                .addRow(
                    ActionRow.of(
                        Button.primary("counter/retry/$seconds", " Mais uma vez!")
                    )
                )
                .withColor(Colors.FUCHSIA)
                .disableMentions()
                .setEphemeral(false)
                .reply(event.getHook())
        }

        if (seconds > 2) {
            // >2s: defer + loading + responde no final
            val finalSeconds1 = seconds
            event.deferEdit().queue(Consumer { hook: InteractionHook ->
                create()
                    .addText("**Contando por $seconds segundo(s)...**")
                    .withColor(Colors.FUCHSIA)
                    .disableMentions()
                    .reply(event.getHook())
                scheduler.submit(countingTask)
                scheduler.schedule(sendResult, finalSeconds1.toLong(), TimeUnit.SECONDS)
            })
        } else {
            // ≤2s: responde imediatamente + conta em background
            scheduler.submit(countingTask)
            scheduler.schedule(sendResult, seconds.toLong(), TimeUnit.SECONDS)

            // Resposta inicial imediata
            create()
                .addText("**Contando por $seconds segundo(s)...**")
                .withColor(Colors.FUCHSIA)
                .disableMentions()
                .editOriginal(event)
        }
    }

    override fun getCustomId(): String {
        return "counter/retry/:seconds"
    }

    private fun extractSeconds(customId: String): Int {
        try {
            val parts: Array<String?> = customId.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            return if (parts.size == 3) parts[2]!!.toInt() else 1
        } catch (e: Exception) {
            System.err.println("Erro ao extrair userId de " + customId + ": " + e.message)
            return 1
        }
    }

    companion object {
        private val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(4)
    }
}