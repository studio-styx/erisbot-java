package utils

import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.components.container.Container
import net.dv8tion.jda.api.components.container.ContainerChildComponent
import net.dv8tion.jda.api.components.mediagallery.MediaGallery
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem
import net.dv8tion.jda.api.components.section.Section
import net.dv8tion.jda.api.components.separator.Separator
import net.dv8tion.jda.api.components.textdisplay.TextDisplay
import net.dv8tion.jda.api.components.thumbnail.Thumbnail
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionHook
import java.awt.Color

object ComponentBuilder {

    // Classe interna com companion object para criar instâncias
    class ContainerBuilder private constructor() {
        private val components = mutableListOf<ContainerChildComponent>()
        private var accentColor: Color? = null
        private var isEphemeral = false
        private var disallowedMentions = false

        // Métodos fluentes
        fun add(component: ContainerChildComponent?) = apply {
            component?.let { components.add(it) }
        }

        fun addText(text: String?) = apply {
            text?.takeIf { it.isNotEmpty() }?.let { components.add(TextDisplay.of(it)) }
        }

        fun addDivider(isLarge: Boolean = false, isInvisible: Boolean = false) = apply {
            val spacing = if (isLarge) Separator.Spacing.LARGE else Separator.Spacing.SMALL
            val separator = if (isInvisible) {
                Separator.createInvisible(spacing)
            } else {
                Separator.createDivider(spacing)
            }
            components.add(separator)
        }

        fun addDivider(isLarge: Boolean = false) = apply {
            val spacing = if (isLarge) Separator.Spacing.LARGE else Separator.Spacing.SMALL
            components.add(Separator.createDivider(spacing))
        }

        fun addSection(thumbnailUrl: String, text: String) = apply {
            components.add(Section.of(Thumbnail.fromUrl(thumbnailUrl), TextDisplay.of(text)))
        }

        fun addSection(button: Button, text: String) = apply {
            components.add(Section.of(button, TextDisplay.of(text)))
        }

        fun addRow(row: ActionRow) = apply {
            components.add(row)
        }

        fun addImage(url: String) = apply {
            components.add(MediaGallery.of(MediaGalleryItem.fromUrl(url)))
        }

        fun withColor(hexColor: String?) = apply {
            hexColor?.takeIf { it.isNotEmpty() }?.let {
                val colorStr = if (it.startsWith("#")) it else "#$it"
                this.accentColor = Color.decode(colorStr)
            }
        }

        fun setEphemeral(ephemeral: Boolean) = apply {
            this.isEphemeral = ephemeral
        }

        fun disableMentions() = apply {
            this.disallowedMentions = true
        }

        fun build(): Container {
            var container = Container.of(components)
            accentColor?.let { container = container.withAccentColor(it) }
            return container
        }

        // === Métodos de reply ===
        fun reply(event: SlashCommandInteractionEvent) = apply {
            if (event.isAcknowledged) {
                // Se já tem defer, edita a original via Hook
                val edit = event.hook.editOriginalComponents(build())
                    .useComponentsV2()
                // Nota: setEphemeral não funciona aqui pois já foi definido no defer

                if (disallowedMentions) {
                    edit.setAllowedMentions(emptyList()).queue()
                } else {
                    edit.queue()
                }
            } else {
                // Se não tem defer, responde criando a mensagem
                val reply = event.replyComponents(build())
                    .useComponentsV2()
                    .setEphemeral(isEphemeral)

                if (disallowedMentions) {
                    reply.setAllowedMentions(emptyList()).queue()
                } else {
                    reply.queue()
                }
            }
        }

        fun reply(event: ButtonInteractionEvent) = apply {
            if (event.isAcknowledged) {
                val edit = event.hook.editOriginalComponents(build())
                    .useComponentsV2()

                if (disallowedMentions) {
                    edit.setAllowedMentions(emptyList()).queue()
                } else {
                    edit.queue()
                }
            } else {
                val reply = event.replyComponents(build())
                    .useComponentsV2()
                    .setEphemeral(isEphemeral)

                if (disallowedMentions) {
                    reply.setAllowedMentions(emptyList()).queue()
                } else {
                    reply.queue()
                }
            }
        }

        fun reply(event: EntitySelectInteractionEvent) = apply {
            if (event.isAcknowledged) {
                val edit = event.hook.editOriginalComponents(build())
                    .useComponentsV2()

                if (disallowedMentions) {
                    edit.setAllowedMentions(emptyList()).queue()
                } else {
                    edit.queue()
                }
            } else {
                val reply = event.replyComponents(build())
                    .useComponentsV2()
                    .setEphemeral(isEphemeral)

                if (disallowedMentions) {
                    reply.setAllowedMentions(emptyList()).queue()
                } else {
                    reply.queue()
                }
            }
        }

        // Para Modal, geralmente se responde com texto ou embed, mas se seu build() retorna componentes:
        fun reply(event: ModalInteractionEvent) = apply {
            if (event.isAcknowledged) {
                val edit = event.hook.editOriginalComponents(build())
                    .useComponentsV2()

                if (disallowedMentions) {
                    edit.setAllowedMentions(emptyList()).queue()
                } else {
                    edit.queue()
                }
            } else {
                val reply = event.replyComponents(build())
                    .useComponentsV2()
                    .setEphemeral(isEphemeral)

                if (disallowedMentions) {
                    reply.setAllowedMentions(emptyList()).queue()
                } else {
                    reply.queue()
                }
            }
        }

        // Método direto para Hook (sempre é edição)
        fun reply(hook: InteractionHook) = apply {
            val edit = hook.editOriginalComponents(build())
                .useComponentsV2()

            if (disallowedMentions) {
                edit.setAllowedMentions(emptyList()).queue()
            } else {
                edit.queue()
            }
        }


// === Métodos de editOriginal (Atualização da mensagem onde o componente está) ===

        fun editOriginal(event: ButtonInteractionEvent) = apply {
            if (event.isAcknowledged) {
                // Se usou deferEdit(), usamos o hook
                val edit = event.hook.editOriginalComponents(build())
                    .useComponentsV2()

                if (disallowedMentions) {
                    edit.setAllowedMentions(emptyList()).queue()
                } else {
                    edit.queue()
                }
            } else {
                // Se não, editamos a mensagem diretamente via evento
                val edit = event.editComponents(build())
                    .useComponentsV2()

                if (disallowedMentions) {
                    edit.setAllowedMentions(emptyList()).queue()
                } else {
                    edit.queue()
                }
            }
        }

        fun editOriginal(event: ModalInteractionEvent) = apply {
            if (event.isAcknowledged) {
                val edit = event.hook.editOriginalComponents(build())
                    .useComponentsV2()

                if (disallowedMentions) {
                    edit.setAllowedMentions(emptyList()).queue()
                } else {
                    edit.queue()
                }
            } else {
                val edit = event.editComponents(build())
                    .useComponentsV2()

                if (disallowedMentions) {
                    edit.setAllowedMentions(emptyList()).queue()
                } else {
                    edit.queue()
                }
            }
        }

        companion object {
            @JvmStatic
            fun create(): ContainerBuilder = ContainerBuilder()
        }
    }
}