package studio.styx.erisbot.core.extensions.jda.reply

import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import utils.ComponentBuilder

fun SlashCommandInteractionEvent.containerFollowUp(
    color: String,
    text: String,
    ephemeral: Boolean = false
) {
    if (this.isAcknowledged) {
        this.hook.sendMessageComponents(
            ComponentBuilder.ContainerBuilder.create().withColor(color).addText(text).build()
        ).useComponentsV2().setEphemeral(ephemeral).queue()
    } else {
        this.replyComponents(
            ComponentBuilder.ContainerBuilder.create().withColor(color).addText(text).build()
        ).useComponentsV2().setEphemeral(ephemeral).queue()
    }
}

fun ButtonInteractionEvent.containerFollowUp(
    color: String,
    text: String,
    ephemeral: Boolean = false
) {
    if (this.isAcknowledged) {
        this.hook.sendMessageComponents(
            ComponentBuilder.ContainerBuilder.create().withColor(color).addText(text).build()
        ).useComponentsV2().setEphemeral(ephemeral).queue()
    } else {
        this.replyComponents(
            ComponentBuilder.ContainerBuilder.create().withColor(color).addText(text).build()
        ).useComponentsV2().setEphemeral(ephemeral).queue()
    }
}

fun ModalInteractionEvent.containerFollowUp(
    color: String,
    text: String,
    ephemeral: Boolean = false
) {
    if (this.isAcknowledged) {
        this.hook.sendMessageComponents(
            ComponentBuilder.ContainerBuilder.create().withColor(color).addText(text).build()
        ).useComponentsV2().setEphemeral(ephemeral).queue()
    } else {
        this.replyComponents(
            ComponentBuilder.ContainerBuilder.create().withColor(color).addText(text).build()
        ).useComponentsV2().setEphemeral(ephemeral).queue()
    }
}