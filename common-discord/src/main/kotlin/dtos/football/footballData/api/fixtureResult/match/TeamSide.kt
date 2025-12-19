package dtos.football.footballData.api.fixtureResult.match

import kotlinx.serialization.Serializable

@Serializable
data class TeamSide(
    val id: Long,
    val name: String,
    val shortName: String,
    val tla: String,
    val crest: String,
)
