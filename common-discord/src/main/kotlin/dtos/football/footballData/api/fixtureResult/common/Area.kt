package dtos.football.footballData.api.fixtureResult.common

import kotlinx.serialization.Serializable

@Serializable
data class Area(
    val id: Long,
    val name: String,
    val code: String,
    val flag: String? = null
)