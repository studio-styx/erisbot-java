package dtos.football.footballData.api.fixtureResult.match

import kotlinx.serialization.Serializable

@Serializable
data class ScoreDetail(
    val home: Int?,
    val away: Int?
)
