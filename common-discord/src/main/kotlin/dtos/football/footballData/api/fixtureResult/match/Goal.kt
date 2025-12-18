package dtos.football.footballData.api.fixtureResult.match

data class Goal(
    val minute: Int,
    val injuryTime: Int,
    val type: String,
    val team: GoalDetail,
    val scorer: GoalDetail,
    val assist: GoalDetail? = null,
    val score: ScoreDetail
)

data class GoalDetail(
    val id: Long,
    val name: String
)