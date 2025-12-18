package dtos.football.footballData.api.fixtureResult.match

data class MatchCompetition(
    val id: Long,
    val name: String,
    val code: String,
    val type: String,
    val emblem: String? = null
)
