package dtos.football.footballData.api.fixtureResult.match

data class TeamSide(
    val id: Long,
    val name: String,
    val shortName: String,
    val tla: String,
    val crest: String,
    val coach: MatchCoach,
    val leagueRank: Int,
    val formation: String,
    val lineUp: List<MatchPlayer>,
    val bench: List<MatchPlayer>
)
