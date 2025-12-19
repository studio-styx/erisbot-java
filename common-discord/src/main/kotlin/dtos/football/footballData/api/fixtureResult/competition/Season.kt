package dtos.football.footballData.api.fixtureResult.competition

import kotlinx.serialization.Serializable

@Serializable
data class Season(
    val id: Long,
    val startDate: String,
    val endDate: String,
    val currentMatchday: Int?,
    val winnerClub: WinnerClub?,
    val stage: List<String>
)