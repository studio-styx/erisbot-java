package database.dtos.personalization.containers

// A classe final que sua aplicação vai usar
data class ContainerComponent(
    val id: Int,
    val containerId: Int,
    val data: ComponentData // Pode ser TextDisplayData (JSON) ou ActionRowData (SQL)
)