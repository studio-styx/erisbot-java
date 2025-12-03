package studio.styx.erisbot.core.extensions.jda.reply

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import utils.ComponentBuilder

fun SlashCommandInteractionEvent.rapidContainerReply(
    color: String,
    text: String,
    ephemeral: Boolean = false
) {
    if (this.isAcknowledged) {
        this.hook.editOriginalComponents(
            ComponentBuilder.ContainerBuilder.create().withColor(color).addText(text).build()
        ).useComponentsV2().queue()
    } else {
        this.replyComponents(
            ComponentBuilder.ContainerBuilder.create().withColor(color).addText(text).build()
        ).useComponentsV2().setEphemeral(ephemeral).queue()
    }
}

fun ButtonInteractionEvent.rapidContainerReply(
    color: String,
    text: String,
    ephemeral: Boolean = false
) {
    if (this.isAcknowledged) {
        this.hook.editOriginalComponents(
            ComponentBuilder.ContainerBuilder.create().withColor(color).addText(text).build()
        ).useComponentsV2().queue()
    } else {
        this.replyComponents(
            ComponentBuilder.ContainerBuilder.create().withColor(color).addText(text).build()
        ).useComponentsV2().setEphemeral(ephemeral).queue()
    }
}