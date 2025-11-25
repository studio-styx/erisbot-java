package studio.styx.erisbot.core.abstractClasses;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.jetbrains.annotations.NotNull;
import org.jooq.DSLContext;
import studio.styx.erisbot.core.interfaces.CommandInterface;

public abstract class AbstractCommand implements CommandInterface {

    protected @NotNull DSLContext dsl;

    public void setDsl(@NotNull DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public abstract void execute(SlashCommandInteractionEvent event);

    @Override
    public abstract SlashCommandData getSlashCommandData();
}