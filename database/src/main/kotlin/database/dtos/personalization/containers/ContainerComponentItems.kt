package database.dtos.personalization.containers

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

// ==========================================
// A Interface Base
// ==========================================
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
    visible = true
)
@JsonSubTypes(
    // Mapeamos apenas o que vem do JSON aqui.
    // O ActionRow será instanciado manualmente pelo código, não pelo Jackson.
    JsonSubTypes.Type(value = TextDisplayData::class, name = "TEXTDISPLAY"),
    JsonSubTypes.Type(value = ImageUrlData::class, name = "IMAGE_URL")
)
sealed interface ComponentData {
    val type: ContainerComponentType
}

// ==========================================
// DTOs baseados em JSON (Coluna details)
// ==========================================
data class TextDisplayData(
    override val type: ContainerComponentType = ContainerComponentType.TEXTDISPLAY,
    val content: String
) : ComponentData

data class ImageUrlData(
    override val type: ContainerComponentType = ContainerComponentType.IMAGE_URL,
    val url: String
) : ComponentData

// ==========================================
// DTOs baseados em SQL (Tabelas ActionRow*)
// ==========================================
// Este DTO é preenchido manualmente via jOOQ, não pelo Jackson
data class ActionRowData(
    override val type: ContainerComponentType = ContainerComponentType.ACTIONROW,
    val components: List<ActionRowElement>
) : ComponentData

sealed interface ActionRowElement {
    val customId: String
}

data class ActionButton(
    override val customId: String,
    val label: String,
    val style: String, // Ou enum ButtonStyle
    val emoji: String?,
    val url: String?,
    val disabled: Boolean
) : ActionRowElement

data class ActionSelectMenu(
    override val customId: String,
    val placeholder: String,
    val minValues: Int,
    val maxValues: Int,
    val options: List<SelectOption>
) : ActionRowElement

data class SelectOption(
    val label: String,
    val value: String,
    val emoji: String?,
    val isDefault: Boolean
)