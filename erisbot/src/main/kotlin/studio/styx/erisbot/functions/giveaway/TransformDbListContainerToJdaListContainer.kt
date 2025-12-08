package studio.styx.erisbot.functions.giveaway

import database.dtos.personalization.containers.*
import net.dv8tion.jda.api.components.MessageTopLevelComponent
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.components.buttons.ButtonStyle
import net.dv8tion.jda.api.components.container.ContainerChildComponent
import net.dv8tion.jda.api.components.mediagallery.MediaGallery
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem
import net.dv8tion.jda.api.components.section.Section
import net.dv8tion.jda.api.components.selections.SelectOption as JdaSelectOption
import net.dv8tion.jda.api.components.selections.StringSelectMenu
import net.dv8tion.jda.api.components.separator.Separator
import net.dv8tion.jda.api.components.textdisplay.TextDisplay
import net.dv8tion.jda.api.components.thumbnail.Thumbnail
import net.dv8tion.jda.api.entities.emoji.Emoji

class TransformDbListContainerToJdaListContainer(
    private val dbComponents: List<ContainerComponent>
) {
    fun transform(): MutableList<MessageTopLevelComponent> {
        val jdaRows = mutableListOf<MessageTopLevelComponent>()

        for (component in dbComponents) {
            jdaRows.addAll(registerComponent(component))
        }

        return jdaRows
    }

    private fun registerComponent(component: ContainerComponent): MutableList<MessageTopLevelComponent> {
        val jdaRows = mutableListOf<MessageTopLevelComponent>()

        when (val data = component.data) {

            is ActionRowData -> {
                val jdaComponents = data.components.map { element ->
                    when (element) {
                        is ActionButton -> {
                            val style = try {
                                ButtonStyle.valueOf(element.style.uppercase())
                            } catch (e: Exception) {
                                ButtonStyle.PRIMARY
                            }

                            val button = if (element.url != null) {
                                Button.link(element.url!!, element.label)
                            } else {
                                Button.of(style, element.customId, element.label)
                            }

                            // Adiciona Emoji se existir
                            val buttonWithEmoji = if (element.emoji != null) {
                                button.withEmoji(Emoji.fromFormatted(element.emoji!!))
                            } else button

                            // Define se está desabilitado
                            buttonWithEmoji.withDisabled(element.disabled)
                        }

                        is ActionSelectMenu -> {
                            val options = element.options.map { opt ->
                                JdaSelectOption.of(opt.label, opt.value)
                                    .withDefault(opt.isDefault)
                                    .let { jdaOpt ->
                                        if (opt.emoji != null) jdaOpt.withEmoji(Emoji.fromFormatted(opt.emoji!!))
                                        else jdaOpt
                                    }
                            }

                            StringSelectMenu.create(element.customId)
                                .setPlaceholder(element.placeholder)
                                .setMinValues(element.minValues)
                                .setMaxValues(element.maxValues)
                                .addOptions(options)
                                .build()
                        }
                    }
                }

                // Cria a ActionRow do JDA apenas se houver componentes válidos dentro
                if (jdaComponents.isNotEmpty()) {
                    jdaRows.add(ActionRow.of(jdaComponents))
                }
            }

            is TextDisplayData -> {
                jdaRows.add(TextDisplay.of(data.content))
            }

            is ImageUrlData -> {
                jdaRows.add(MediaGallery.of(MediaGalleryItem.fromUrl(data.url)))
            }

            is SectionButton -> {
                val button = run {
                    val element = data.button
                    val style = try {
                        ButtonStyle.valueOf(element.style.uppercase())
                    } catch (e: Exception) {
                        ButtonStyle.PRIMARY
                    }

                    val button = if (element.url != null) {
                        Button.link(element.url!!, element.label)
                    } else {
                        Button.of(style, element.customId, element.label)
                    }

                    // Adiciona Emoji se existir
                    val buttonWithEmoji = if (element.emoji != null) {
                        button.withEmoji(Emoji.fromFormatted(element.emoji!!))
                    } else button

                    // Define se está desabilitado
                    buttonWithEmoji.withDisabled(element.disabled)
                }

                jdaRows.add(
                    Section.of(button, TextDisplay.of(data.text.content))
                )
            }

            is SectionThumbnail -> {
                jdaRows.add(
                    Section.of(Thumbnail.fromUrl(data.thumbnail.url), TextDisplay.of(data.text.content))
                )
            }

            is Divider -> {
                if (data.isInvisible) {
                    jdaRows.add(Separator.createInvisible(
                        if (data.isLarge)
                            Separator.Spacing.LARGE
                        else
                            Separator.Spacing.SMALL
                    ))
                } else {
                    jdaRows.add(Separator.createDivider(
                        if (data.isLarge)
                            Separator.Spacing.LARGE
                        else
                            Separator.Spacing.SMALL
                    ))
                }
            }

            is Container -> {
                val containerRows = mutableListOf<ContainerChildComponent>()
                for (containerComponent in data.components) {
                    containerRows.addAll(registerComponent(containerComponent) as MutableList<ContainerChildComponent>)
                }
                val container = net.dv8tion.jda.api.components.container.Container.of(containerRows)
                jdaRows.add(container)
            }
        }

        return jdaRows
    }
}