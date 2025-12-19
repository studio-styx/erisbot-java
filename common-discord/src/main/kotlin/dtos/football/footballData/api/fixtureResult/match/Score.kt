package dtos.football.footballData.api.fixtureResult.match

import kotlinx.serialization.Serializable

@Serializable
data class Score(
    val winner: ScoreWinner? = null,
    val duration: String,
    val fullTime: ScoreDetail,
    val halfTime: ScoreDetail
)

@Serializable
enum class ScoreWinner {
    HOME_TEAM,
    AWAY_TEAM,
    DRAW
}