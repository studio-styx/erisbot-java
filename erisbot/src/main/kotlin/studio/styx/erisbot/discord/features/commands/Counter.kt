package studio.styx.erisbot.discord.features.commands

import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.DiscordLocale
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import org.springframework.stereotype.Component
import shared.Colors
import shared.utils.Utils.formatNumber
import studio.styx.erisbot.core.interfaces.CommandInterface
import utils.ComponentBuilder.ContainerBuilder.Companion.create
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer


@Component
class Counter : CommandInterface {
    override fun execute(event: SlashCommandInteractionEvent) {
        var seconds = if (event.getOption("time") != null) event.getOption("time")!!.getAsInt() else 1
        seconds = Math.clamp(seconds.toLong(), 1, 15)

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

            val message: String = """
            **Contagem concluida!** 
            
            Tempo: **$seconds segundos**
            Eu contei até: **${formatNumber(finalCount)}**
            Velocidade média: **${formatNumber(speed)} por segundo**
            """
            create()
                .addText(message)
                .addRow(
                    ActionRow.of(
                        Button.primary("counter/retry/" + finalSeconds, " Contar novamente")
                    )
                )
                .withColor(Colors.FUCHSIA)
                .disableMentions()
                .setEphemeral(false)
                .reply(event.getHook())
        }

        if (seconds > 2) {
            create()
                .addText("**Contando por $seconds segundo(s)...**")
                .withColor(Colors.FUCHSIA)
                .disableMentions()
                .reply(event.getHook())
            // >2s: defer + loading + responde no final
            val finalSeconds1 = seconds
            event.deferReply().queue(Consumer { hook: InteractionHook? ->
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
                .reply(event)
        }
    }

    override fun getSlashCommandData(): SlashCommandData {
        val timeOption = OptionData(OptionType.INTEGER, "time", "⏱️ ✦ Time in seconds (1-15)", false)
            .setMinValue(1)
            .setMaxValue(15)
            .setNameLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "tempo")
            .setDescriptionLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "⏱️ ✦ Tempo em segundos (1-15)")
            .setNameLocalization(DiscordLocale.SPANISH, "tiempo")
            .setDescriptionLocalization(DiscordLocale.SPANISH, "⏱️ ✦ Tiempo en segundos (1-15)")
            .setNameLocalization(DiscordLocale.ENGLISH_US, "time")
            .setDescriptionLocalization(DiscordLocale.ENGLISH_US, "⏱️ ✦ Time in seconds (1-15)")
            .setNameLocalization(DiscordLocale.SPANISH_LATAM, "tiempo")
            .setDescriptionLocalization(DiscordLocale.SPANISH_LATAM, "⏱️ ✦ Tiempo en segundos (1-15)")

        return Commands.slash("counter", "🔢 ✦ Make me count for you!")
            .addOptions(timeOption)
            .setNameLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "contador")
            .setDescriptionLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "🔢 ✦ Faça eu contar até o máximo possivel")
            .setNameLocalization(DiscordLocale.SPANISH, "contador")
            .setNameLocalization(DiscordLocale.SPANISH_LATAM, "contador")
            .setDescriptionLocalization(DiscordLocale.SPANISH, "🔢 ✦ Hazme contar hasta el máximo posible")
            .setDescriptionLocalization(DiscordLocale.SPANISH_LATAM, "🔢 ✦ Hazme contar hasta el máximo posible")
            .setNameLocalization(DiscordLocale.ENGLISH_US, "counter")
            .setDescriptionLocalization(DiscordLocale.ENGLISH_US, "🔢 ✦ Make me count for you!")
    }

    companion object {
        private val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(4)
    }
}