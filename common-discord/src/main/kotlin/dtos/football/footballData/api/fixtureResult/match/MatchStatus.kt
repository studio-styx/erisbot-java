package dtos.football.footballData.api.fixtureResult.match

import kotlinx.serialization.Serializable

@Serializable
enum class MatchStatus {
    SCHEDULED,
    LIVE,
    PAUSED,
    FINISHED,
    POSTPONED,
    CANCELLED,
    SUSPENDED,
    AWARDED,
    TIMED,
    IN_PLAY
}