package dtos.football.footballData.api.fixtureResult.common

data class Area(
    val id: Long,
    val name: String,
    val code: String,
    val flag: String? = null
)