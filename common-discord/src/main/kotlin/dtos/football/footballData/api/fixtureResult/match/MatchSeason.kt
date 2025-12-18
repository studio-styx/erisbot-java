package dtos.football.footballData.api.fixtureResult.match

data class MatchSeason(
    val id: Long,
    val startDate: String,
    val endDate: String,
    val currentMatchDay: Int?,
    val winnerClub: MatchWinnerClub?,
    val stage: List<String>
)

data class MatchWinnerClub(
    val id: Long,
    val name: String,
)