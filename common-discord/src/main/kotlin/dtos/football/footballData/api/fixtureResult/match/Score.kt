package dtos.football.footballData.api.fixtureResult.match

data class Score(
    val winner: ScoreWinner,
    val duration: String,
    val fullTime: ScoreDetail,
    val halfTime: ScoreDetail
)

enum class ScoreWinner {
    HOME_TEAM,
    AWAY_TEAM,
    DRAW
}