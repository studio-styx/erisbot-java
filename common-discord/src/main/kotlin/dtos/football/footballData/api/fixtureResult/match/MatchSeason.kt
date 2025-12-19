package dtos.football.footballData.api.fixtureResult.match

import kotlinx.serialization.Serializable

@Serializable
data class MatchSeason(
    val id: Long,
    val startDate: String,
    val endDate: String,
    val currentMatchday: Int?
)

@Serializable
data class MatchWinnerClub(
    val id: Long,
    val name: String,
)