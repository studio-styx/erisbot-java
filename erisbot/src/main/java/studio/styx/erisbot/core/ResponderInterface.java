package studio.styx.erisbot.core;

import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.components.selections.SelectMenuInteraction;

public interface ResponderInterface {
    // Método obrigatório
    String getCustomId();

    // Métodos default (opcionais)
    default void execute(StringSelectInteractionEvent event) {
        // Implementação vazia por padrão
    }

    default void execute(ButtonInteractionEvent event) {
        // Implementação vazia por padrão
    }

    default void execute(SelectMenuInteraction event) {
        // Implementação vazia por padrão
    }

    default void execute(ModalInteractionEvent event) {
        // Implementação vazia por padrão
    }
}