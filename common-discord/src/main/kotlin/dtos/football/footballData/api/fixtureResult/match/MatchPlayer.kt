package dtos.football.footballData.api.fixtureResult.match

import kotlinx.serialization.Serializable

@Serializable
data class MatchPlayer(
    val id: Long,
    val name: String,
    val position: String,
    val shirtNumber: Int,
)