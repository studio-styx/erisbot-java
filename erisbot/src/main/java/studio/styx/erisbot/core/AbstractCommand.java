package studio.styx.erisbot.core;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.jooq.DSLContext;

public abstract class AbstractCommand implements CommandInterface {

    protected DSLContext dsl;

    public void setDsl(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public abstract void execute(SlashCommandInteractionEvent event);

    @Override
    public abstract SlashCommandData getSlashCommandData();
}