package studio.styx.erisbot.core.interfaces

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData

interface CommandInterface {
    suspend fun execute(event: SlashCommandInteractionEvent)

    fun getSlashCommandData(): SlashCommandData
}
