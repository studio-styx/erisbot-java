package studio.styx.erisbot.core.abstractClasses

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import org.jooq.DSLContext
import studio.styx.erisbot.core.interfaces.CommandInterface

abstract class AbstractCommand : CommandInterface {
    var dsl: DSLContext? = null

    abstract override suspend fun execute(event: SlashCommandInteractionEvent)

    abstract override fun getSlashCommandData(): SlashCommandData
}