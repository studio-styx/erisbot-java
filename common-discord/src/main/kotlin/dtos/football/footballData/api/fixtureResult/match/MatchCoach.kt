package dtos.football.footballData.api.fixtureResult.match

import kotlinx.serialization.Serializable

@Serializable
data class MatchCoach(
    val id: Long,
    val name: String,
    val nationality: String,
)
