package dtos.football.footballData.api.fixtureResult.team

data class TeamCompetition(
    val id: Long,
    val name: String,
    val code: String,
    val type: String,
    val emblem: String? = null
)
