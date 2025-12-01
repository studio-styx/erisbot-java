package studio.styx.erisbot.core.interfaces

import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent

interface ResponderInterface {
    // Obrigatório implementar
    val customId: String

    // Opcionais (já vêm com corpo vazio, então não precisa implementar se não usar)
    suspend fun execute(event: StringSelectInteractionEvent) {}

    suspend fun execute(event: ButtonInteractionEvent) {}

    suspend fun execute(event: ModalInteractionEvent) {}

    suspend fun execute(event: EntitySelectInteractionEvent) {}
}