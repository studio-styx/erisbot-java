package studio.styx.erisbot.utils;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class ComponentBuilder {

    public static class ContainerBuilder {
        private final List<ContainerChildComponent> components = new ArrayList<>();
        private Color accentColor;
        private boolean isEphemeral = false;
        private boolean disallowedMentions = false;

        private ContainerBuilder() {
            // Construtor privado, use métodos estáticos para iniciar
        }

        // Inicia um novo builder
        public static ContainerBuilder create() {
            return new ContainerBuilder();
        }

        // Adiciona um componente genérico (TextDisplay, Separator, Button, etc.)
        public ContainerBuilder add(ContainerChildComponent component) {
            if (component != null) {
                components.add(component);
            }
            return this;
        }

        // Adiciona um texto simples (converte String para TextDisplay automaticamente)
        public ContainerBuilder addText(String text) {
            if (text != null && !text.isEmpty()) {
                components.add(TextDisplay.of(text));
            }
            return this;
        }

        // Adiciona um divisor com espaçamento específico
        public ContainerBuilder addDivider(@NotNull Boolean isLarge) {
            if (isLarge) {
                components.add(Separator.createDivider(Separator.Spacing.LARGE));
            } else {
                components.add(Separator.createDivider(Separator.Spacing.SMALL));
            }
            return this;
        }

        // Adiciona um divisor com espaçamento invisivel
        public ContainerBuilder addDivider(@NotNull Boolean isInvisble, Boolean isLarge) {
            if (isInvisble) {
                if (isLarge) {
                    components.add(Separator.createInvisible(Separator.Spacing.LARGE));
                } else {
                    components.add(Separator.createInvisible(Separator.Spacing.SMALL));
                }
            } else {
                if (isLarge) {
                    components.add(Separator.createDivider(Separator.Spacing.LARGE));
                } else {
                    components.add(Separator.createDivider(Separator.Spacing.SMALL));
                }
            }
            return this;
        }

        public ContainerBuilder addSection(@NotNull String thumbnailUrl, String text) {
            components.add(
                    Section.of(
                            Thumbnail.fromUrl(thumbnailUrl),
                            TextDisplay.of(text)
                    )
            );

            return this;
        }

        public ContainerBuilder addSection(@NotNull Button button, String text) {
            components.add(
                    Section.of(
                            button,
                            TextDisplay.of(text)
                    )
            );
            return this;
        }

        public ContainerBuilder addRow(@NotNull ActionRow row) {
            components.add(row);
            return this;
        }

        // Define a cor de destaque
        public ContainerBuilder withColor(String hexColor) {
            if (hexColor != null && !hexColor.isEmpty()) {
                // Adiciona o # se não tiver
                String colorString = hexColor.startsWith("#") ? hexColor : "#" + hexColor;
                this.accentColor = Color.decode(colorString);
            }
            return this;
        }

        // Define se a mensagem é efêmera
        public ContainerBuilder setEphemeral(boolean ephemeral) {
            this.isEphemeral = ephemeral;
            return this;
        }

        // Constrói o container
        public Container build() {
            Container container = Container.of(this.components);
            if (accentColor != null) {
                container = container.withAccentColor(accentColor);
            }
            return container;
        }

        public ContainerBuilder disableMentions() {
            this.disallowedMentions = true;
            return this;
        }

        // Envia a resposta diretamente
        public void reply(SlashCommandInteraction event) {
            if (this.disallowedMentions) {
                event.replyComponents(build())
                        .useComponentsV2()
                        .setEphemeral(isEphemeral)
                        .setAllowedMentions(EnumSet.noneOf(Message.MentionType.class))
                        .queue();
            } else {
                event.replyComponents(build())
                        .useComponentsV2()
                        .setEphemeral(isEphemeral)
                        .queue();
            }
        }
        public void reply(ButtonInteractionEvent event) {
            if (this.disallowedMentions) {
                event.replyComponents(build())
                        .useComponentsV2()
                        .setEphemeral(isEphemeral)
                        .setAllowedMentions(EnumSet.noneOf(Message.MentionType.class))
                        .queue();
            } else {
                event.replyComponents(build())
                        .useComponentsV2()
                        .setEphemeral(isEphemeral)
                        .queue();
            }
        }

        public void reply(InteractionHook event) {
            if (this.disallowedMentions) {
                event.editOriginalComponents(build())
                        .useComponentsV2()
                        .setAllowedMentions(EnumSet.noneOf(Message.MentionType.class))
                        .queue();
            } else {
                event.editOriginalComponents(build())
                        .useComponentsV2()
                        .queue();
            }
        }

        public void editOriginal(ButtonInteractionEvent event) {
            if (this.disallowedMentions) {
                event.editComponents(build())
                        .useComponentsV2()
                        .setAllowedMentions(EnumSet.noneOf(Message.MentionType.class))
                        .queue();
            } else {
                event.editComponents(build())
                        .useComponentsV2()
                        .setAllowedMentions(EnumSet.noneOf(Message.MentionType.class))
                        .queue();
            }
        }
    }
}