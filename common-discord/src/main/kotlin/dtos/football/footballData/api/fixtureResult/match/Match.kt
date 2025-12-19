package dtos.football.footballData.api.fixtureResult.match

import kotlinx.serialization.Serializable

@Serializable
data class Match(
    val id: Long,
    val competition: MatchCompetition,
    val season: MatchSeason,
    val utcDate: String,
    val status: MatchStatus,
    val attendance: Int? = null,
    val venue: String? = null,
    val matchday: Int,
    val stage: MatchStages,
    val group: String? = null,
    val lastUpdated: String,

    val homeTeam: TeamSide,
    val awayTeam: TeamSide,

    val score: Score? = null,
)

@Serializable
data class Matches(
    val matches: List<Match>,
    val filters: Filters,
    val resultSet: ResultSet
)

@Serializable
data class Filters(
    val dateFrom: String,
    val dateTo: String,
    val permission: String
)

@Serializable
data class ResultSet(
    val count: Int,
    val competitions: String,
    val first: String,
    val last: String,
    val played: Int
)