package dtos.football.footballData.api.fixtureResult.match

data class Penalty(
    val player: GoalDetail,
    val team: GoalDetail?,
    val scored: Boolean
)
