package dtos.football.footballData.api.fixtureResult.match

import kotlinx.serialization.Serializable

@Serializable
data class Booking(
    val minute: Int,
    val team: GoalDetail,
    val player: GoalDetail,
    val card: CardType
)

enum class CardType {
    YELLOW,
    RED,
    YELLOW_RED,
}