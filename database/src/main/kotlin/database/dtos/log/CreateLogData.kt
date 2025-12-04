package database.dtos.log

data class CreateLogData(
    val userId: String,
    val message: String,
    val level: Int = 1,
    val type: String = "info",
    val tags: List<String> = emptyList()
)