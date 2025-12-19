package dtos.football.footballData.api.fixtureResult.competition

import kotlinx.serialization.Serializable

@Serializable
data class WinnerClub(
    val id: Long,
    val name: String,
    val shortName: String,
    val tla: String,
    val address: String,
    val phone: String,
    val website: String,
    val founded: Int, // Ano de fundação
    val clubColors: String,
    val venue: String,
    val lastUpdated: String // Data da última atualização
)
