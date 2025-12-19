package dtos.football.footballData.api.fixtureResult.match

import kotlinx.serialization.Serializable

@Serializable
data class Penalty(
    val player: GoalDetail,
    val team: GoalDetail?,
    val scored: Boolean
)
