package dtos.football.footballData.api.fixtureResult.team

data class Coach(
    val id: Long,
    val firstName: String,
    val lastName: String,
    val name: String,
    val nationality: String,
    val contract: Contract,
    val dateOfBirth: String // Data de nascimento (ISO: "1953-09-16")
)
