package dtos.football.footballData.api.fixtureResult.competition

import dtos.football.footballData.api.fixtureResult.common.Area
import kotlinx.serialization.Serializable

@Serializable
data class Competition(
    val id: Long,
    val name: String,
    val code: String,
    val type: String,
    val emblem: String? = null,
    val area: Area,
    val currentSeason: Season? = null,
    val seasons: List<Season>,
    val lastUpdated: String // Data da última atualização
)
