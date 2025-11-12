package studio.styx.erisbot.core;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public interface CommandInterface {
    void execute(SlashCommandInteractionEvent event);

    SlashCommandData getSlashCommandData();
}
