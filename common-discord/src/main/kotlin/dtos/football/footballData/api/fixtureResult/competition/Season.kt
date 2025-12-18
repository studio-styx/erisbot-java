package dtos.football.footballData.api.fixtureResult.competition

data class Season(
    val id: Long,
    val startDate: String,
    val endDate: String,
    val currentMatchDay: Int?,
    val winnerClub: WinnerClub?,
    val stage: List<String>
)