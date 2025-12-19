package dtos.football.footballData.api.fixtureResult.team

import kotlinx.serialization.Serializable

@Serializable
data class Contract(
    val start: String,
    val until: String
)
