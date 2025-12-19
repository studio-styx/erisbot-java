package dtos.football.footballData.api.fixtureResult.match

import kotlinx.serialization.Serializable

@Serializable
data class MatchCompetition(
    val id: Long,
    val name: String,
    val code: String,
    val type: String,
    val emblem: String? = null
)
