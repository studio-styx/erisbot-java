package studio.styx.erisbot.discord.config

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.requests.GatewayIntent
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import studio.styx.erisbot.core.DiscordConfig
import java.util.EnumSet

@Configuration
open class JdaConfiguration(
    private val discordConfig: DiscordConfig,
    private val listeners: List<ListenerAdapter>
) {

    @Bean
    open fun jda(): JDA {
        println("Iniciando JDA com ${listeners.size} listeners registrados automaticamente.")

        return JDABuilder.createLight(
            discordConfig.token,
            EnumSet.of(
                GatewayIntent.GUILD_MESSAGES,
                GatewayIntent.MESSAGE_CONTENT,
                GatewayIntent.GUILD_MEMBERS
            )
        )
            .addEventListeners(*listeners.toTypedArray())
            .build()
            .also { it.awaitReady() }
    }
}