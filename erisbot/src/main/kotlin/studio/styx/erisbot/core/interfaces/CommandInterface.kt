package studio.styx.erisbot.core.interfaces

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent // Import novo
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData

interface CommandInterface {
    suspend fun execute(event: SlashCommandInteractionEvent)

    suspend fun onAutoComplete(event: CommandAutoCompleteInteractionEvent) {
        // Por padrão, não faz nada.
    }

    fun getSlashCommandData(): SlashCommandData
}