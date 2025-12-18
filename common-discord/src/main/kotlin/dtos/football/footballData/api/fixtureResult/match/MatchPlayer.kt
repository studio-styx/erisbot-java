package dtos.football.footballData.api.fixtureResult.match

data class MatchPlayer(
    val id: Long,
    val name: String,
    val position: String,
    val shirtNumber: Int,
)