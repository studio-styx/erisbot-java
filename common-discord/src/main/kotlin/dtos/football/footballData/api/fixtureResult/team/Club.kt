package dtos.football.footballData.api.fixtureResult.team

import dtos.football.footballData.api.fixtureResult.common.Area
import kotlinx.serialization.Serializable

@Serializable
data class Club(
    val id: Long,
    val name: String,
    val shortName: String,
    val area: Area,
    val tla: String,
    val crest: String,
    val address: String,
    val phone: String,
    val website: String,
    val founded: Int, // Ano de fundação
    val clubColors: String,
    val venue: String,
    val runningCompetitions: List<TeamCompetition>,
    val staff: List<Staff>,
    val coach: Coach,
    val squad: List<Player>,
    val lastUpdated: String // Data da última atualização
)
