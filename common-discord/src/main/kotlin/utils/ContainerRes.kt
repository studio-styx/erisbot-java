package utils

import net.dv8tion.jda.api.components.container.Container
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction

class ContainerRes {
    private var ephemeral: Boolean = false
    private var color: String? = null
    private var text: String? = null

    fun setEphemeral() = apply { ephemeral = true }
    fun setEphemeral(ephemeral: Boolean) = apply { this.ephemeral = ephemeral }

    fun setColor(color: String) = apply { this.color = color }

    fun setText(text: String) = apply { this.text = text }

    fun send(event: SlashCommandInteraction) {
        ComponentBuilder.ContainerBuilder.create()
            .setEphemeral(ephemeral)
            .withColor(color)
            .addText(text)
            .reply(event)
    }

    fun send(hook: InteractionHook) {
        ComponentBuilder.ContainerBuilder.create()
            .setEphemeral(ephemeral)
            .withColor(color)
            .addText(text)
            .reply(hook)
    }

    fun send(event: ButtonInteractionEvent) {
        ComponentBuilder.ContainerBuilder.create()
            .setEphemeral(ephemeral)
            .withColor(color)
            .addText(text)
            .reply(event)
    }

    fun send(event: ModalInteractionEvent) {
        ComponentBuilder.ContainerBuilder.create()
            .setEphemeral(ephemeral)
            .withColor(color)
            .addText(text)
            .reply(event)
    }

    fun edit(event: ButtonInteractionEvent) {
        ComponentBuilder.ContainerBuilder.create()
            .setEphemeral(ephemeral)
            .withColor(color)
            .addText(text)
            .editOriginal(event)
    }

    fun edit(event: ModalInteractionEvent) {
        ComponentBuilder.ContainerBuilder.create()
            .setEphemeral(ephemeral)
            .withColor(color)
            .addText(text)
            .editOriginal(event)
    }

    fun edit(hook: InteractionHook) {
        ComponentBuilder.ContainerBuilder.create()
            .setEphemeral(ephemeral)
            .withColor(color)
            .addText(text)
            .reply(hook)
    }

    fun send(event: EntitySelectInteractionEvent) {
        ComponentBuilder.ContainerBuilder.create()
            .setEphemeral(ephemeral)
            .withColor(color)
            .addText(text)
            .reply(event)
    }

    fun build(): Container {
        return ComponentBuilder.ContainerBuilder.create()
            .setEphemeral(ephemeral)
            .withColor(color)
            .addText(text)
            .build()
    }
}