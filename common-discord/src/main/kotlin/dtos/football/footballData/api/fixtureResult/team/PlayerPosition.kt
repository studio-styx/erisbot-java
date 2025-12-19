package dtos.football.footballData.api.fixtureResult.team

import kotlinx.serialization.Serializable

@Serializable
enum class PlayerPosition {
    Goalkeeper,
    Defender,
    Midfielder,
    Offence
}