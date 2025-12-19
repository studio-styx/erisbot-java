package dtos.football.footballData.api.fixtureResult.team

import kotlinx.serialization.Serializable

@Serializable
data class Player(
    val id: Long,
    val firstName: String,
    val lastName: String,
    val name: String,
    val nationality: String,
    val contract: Contract,
    val dateOfBirth: String, // Data de nascimento (ISO: "1953-09-16")
    val position: PlayerPosition,
    val shirtNumber: Int,
    val marketValue: Long? = null
)
