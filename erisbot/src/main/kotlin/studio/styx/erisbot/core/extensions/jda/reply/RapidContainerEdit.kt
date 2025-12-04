package studio.styx.erisbot.core.extensions.jda.reply

import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import utils.ComponentBuilder

fun ButtonInteractionEvent.rapidContainerEdit(
    color: String,
    text: String
) {
    if (this.isAcknowledged) {
        this.hook.editOriginalComponents(
            ComponentBuilder.ContainerBuilder.create().withColor(color).addText(text).build()
        ).useComponentsV2().queue()
    } else {
        this.editComponents(
            ComponentBuilder.ContainerBuilder.create().withColor(color).addText(text).build()
        ).useComponentsV2().queue()
    }
}

fun ModalInteractionEvent.rapidContainerEdit(
    color: String,
    text: String
) {
    if (this.isAcknowledged) {
        this.hook.editOriginalComponents(
            ComponentBuilder.ContainerBuilder.create().withColor(color).addText(text).build()
        ).useComponentsV2().queue()
    } else {
        this.editComponents(
            ComponentBuilder.ContainerBuilder.create().withColor(color).addText(text).build()
        ).useComponentsV2().queue()
    }
}