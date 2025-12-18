package functions.football.register

import database.extensions.transactionSuspend
import dtos.football.footballData.api.fixtureResult.common.Area
import dtos.football.footballData.api.fixtureResult.match.Match
import dtos.football.footballData.api.fixtureResult.match.MatchStatus
import dtos.football.footballData.api.fixtureResult.match.TeamSide
import dtos.football.footballData.api.fixtureResult.team.Player
import dtos.football.footballData.api.fixtureResult.team.PlayerPosition
import functions.football.ApiFootballDataSdk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.DSLContext
import studio.styx.erisbot.generated.enums.Matchstatus
import studio.styx.erisbot.generated.tables.references.FOOTBALLAREA
import studio.styx.erisbot.generated.tables.references.FOOTBALLLEAGUE
import studio.styx.erisbot.generated.tables.references.FOOTBALLMATCH
import studio.styx.erisbot.generated.tables.references.FOOTBALLPLAYER
import studio.styx.erisbot.generated.tables.references.FOOTBALLTEAM
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

interface FootballMatchesPipelineResult {
    val success: List<Match>
    val failed: List<Match>
    val errors: List<Throwable>
    val durationMinutes: Int
    val startedAt: LocalDateTime
    val finishedAt: LocalDateTime
}

class RegisterMatches(
    private val sdk: ApiFootballDataSdk,
    private val dsl: DSLContext
) {
    private val processedLeagues = mutableSetOf<Long>()
    private val processedTeams = mutableSetOf<Long>()

    private val defaultArea = Area(
        id = 0,
        name = "Desconhecido",
        code = "UNK",
    )

    suspend fun registerFootballMatches(): FootballMatchesPipelineResult {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val apiResult = sdk.matches.getGamesByRange(now.minusDays(2).toLocalDate(), now.plusDays(7).toLocalDate())

        val success = mutableListOf<Match>()
        val failed = mutableListOf<Match>()
        val errors = mutableListOf<Throwable>()

        for (match in apiResult.matches) {
            try {
                // 1. Garante que as dependências existem
                ensureLeagueExists(match)
                ensureTeamExists(match.homeTeam, match.competition.id)
                ensureTeamExists(match.awayTeam, match.competition.id)

                // 2. Registra a partida
                handleRegisterMatchResult(match)

                success.add(match)
            } catch (e: Exception) {
                e.printStackTrace()
                errors.add(e)
                failed.add(match)
            }
        }

        val finishedAt = OffsetDateTime.now(ZoneOffset.UTC)
        val duration = java.time.Duration.between(now, finishedAt)

        return object : FootballMatchesPipelineResult {
            override val success = success
            override val failed = failed
            override val errors = errors
            override val durationMinutes = duration.toMinutes().toInt()
            override val startedAt = now.toLocalDateTime()
            override val finishedAt = finishedAt.toLocalDateTime()
        }
    }

    private suspend fun ensureLeagueExists(match: Match) {
        if (processedLeagues.contains(match.competition.id)) return

        val exists = withContext(Dispatchers.IO) {
            dsl.fetchExists(FOOTBALLLEAGUE, FOOTBALLLEAGUE.APIID.eq(match.competition.id))
        }

        if (!exists) {
            val info = runCatching { sdk.competitions.get(match.competition.code).getInfo() }.getOrNull()

            dsl.transactionSuspend { config ->
                val tx = config.dsl()
                val areaId = upsertArea(tx, info?.area ?: defaultArea)

                tx.insertInto(FOOTBALLLEAGUE)
                    .set(FOOTBALLLEAGUE.APIID, match.competition.id)
                    .set(FOOTBALLLEAGUE.NAME, info?.name ?: match.competition.name)
                    .set(FOOTBALLLEAGUE.CODE, info?.code ?: match.competition.code)
                    .set(FOOTBALLLEAGUE.TYPE, info?.type ?: match.competition.type)
                    .set(FOOTBALLLEAGUE.EMBLEM, info?.emblem)
                    .set(FOOTBALLLEAGUE.AREAID, areaId.toInt())
                    .onDuplicateKeyIgnore()
                    .execute()
            }
        }
        processedLeagues.add(match.competition.id)
    }

    private suspend fun ensureTeamExists(teamSide: TeamSide, competitionApiId: Long) {
        if (processedTeams.contains(teamSide.id)) return

        val exists = withContext(Dispatchers.IO) {
            dsl.fetchExists(FOOTBALLTEAM, FOOTBALLTEAM.APIID.eq(teamSide.id))
        }

        if (!exists) {
            val info = runCatching { sdk.teams.get(teamSide.id).getInfo() }.getOrNull()

            dsl.transactionSuspend { config ->
                val tx = config.dsl()
                val areaId = upsertArea(tx, info?.area ?: defaultArea)

                // Insere o Time
                val teamId = tx.insertInto(FOOTBALLTEAM)
                    .set(FOOTBALLTEAM.APIID, teamSide.id)
                    .set(FOOTBALLTEAM.NAME, info?.name ?: teamSide.name)
                    .set(FOOTBALLTEAM.SHORTNAME, info?.shortName ?: teamSide.shortName)
                    .set(FOOTBALLTEAM.AREAID, areaId.toInt())
                    .set(FOOTBALLTEAM.TLA, info?.tla ?: teamSide.tla)
                    .set(FOOTBALLTEAM.CREST, info?.crest ?: teamSide.crest)
                    .set(FOOTBALLTEAM.ADDRESS, info?.address)
                    .set(FOOTBALLTEAM.CLUBCOLORS, info?.clubColors)
                    .set(FOOTBALLTEAM.VENUE, info?.venue)
                    .onDuplicateKeyIgnore()
                    .returning(FOOTBALLTEAM.ID)
                    .fetchOne {
                        it[FOOTBALLTEAM.ID]!!
                    }!!

                // Insere Jogadores (Squad) se existirem no info
                info?.squad?.forEach { player ->
                    upsertPlayer(tx, player, teamId)
                }
            }
        }
        processedTeams.add(teamSide.id)
    }

    private fun upsertArea(tx: DSLContext, area: Area): Long {
        tx.insertInto(FOOTBALLAREA)
            .set(FOOTBALLAREA.ID, area.id.toInt())
            .set(FOOTBALLAREA.NAME, area.name)
            .set(FOOTBALLAREA.CODE, area.code)
            .set(FOOTBALLAREA.FLAG, area.flag)
            .onDuplicateKeyUpdate()
            .set(FOOTBALLAREA.NAME, area.name)
            .execute()
        return area.id
    }

    private fun upsertPlayer(tx: DSLContext, p: Player, teamId: Long) {
        tx.insertInto(FOOTBALLPLAYER)
            .set(FOOTBALLPLAYER.APIID, p.id.toInt())
            .set(FOOTBALLPLAYER.NAME, p.name)
            .set(FOOTBALLPLAYER.POSITION, PlayerPosition.valueOf(p.position.toString()).toString())
            .set(FOOTBALLPLAYER.NATIONALITY, p.nationality)
            .set(FOOTBALLPLAYER.TEAMID, teamId)
            .onDuplicateKeyUpdate()
            .set(FOOTBALLPLAYER.NAME, p.name)
            .set(FOOTBALLPLAYER.POSITION, PlayerPosition.valueOf(p.position.toString()).toString())
            .execute()
    }

    private suspend fun handleRegisterMatchResult(match: Match) {
        // Buscamos os IDs internos necessários para as Foreign Keys
        val internalIds = withContext(Dispatchers.IO) {
            val leagueId = dsl.select(FOOTBALLLEAGUE.ID).from(FOOTBALLLEAGUE).where(FOOTBALLLEAGUE.APIID.eq(match.competition.id)).fetchOne(FOOTBALLLEAGUE.ID)
            val homeId = dsl.select(FOOTBALLTEAM.ID).from(FOOTBALLTEAM).where(FOOTBALLTEAM.APIID.eq(match.homeTeam.id)).fetchOne(FOOTBALLTEAM.ID)
            val awayId = dsl.select(FOOTBALLTEAM.ID).from(FOOTBALLTEAM).where(FOOTBALLTEAM.APIID.eq(match.awayTeam.id)).fetchOne(FOOTBALLTEAM.ID)
            Triple(leagueId, homeId, awayId)
        }

        val (leagueDbId, homeDbId, awayDbId) = internalIds

        dsl.transactionSuspend { config ->
            val tx = config.dsl()

            // Status Mapper (Igual ao seu TS: TIMED -> SCHEDULED)
            val dbStatus = when(match.status) {
                MatchStatus.TIMED -> Matchstatus.SCHEDULED
                MatchStatus.CANCELLED -> Matchstatus.CANCELED
                else -> Matchstatus.valueOf(match.status.toString())
            }

            // Upsert da Partida
            tx.insertInto(FOOTBALLMATCH)
                .set(FOOTBALLMATCH.APIID, match.id.toInt())
                .set(FOOTBALLMATCH.COMPETITIONID, leagueDbId)
                .set(FOOTBALLMATCH.HOMETEAMID, homeDbId)
                .set(FOOTBALLMATCH.AWAYTEAMID, awayDbId)
                .set(FOOTBALLMATCH.GOALSHOME, match.score?.fullTime?.home ?: 0)
                .set(FOOTBALLMATCH.GOALSAWAY, match.score?.fullTime?.away ?: 0)
                .set(FOOTBALLMATCH.STATUS, dbStatus)
                .set(FOOTBALLMATCH.STARTAT, OffsetDateTime.parse(match.utcDate).toLocalDateTime())
                .onDuplicateKeyUpdate()
                .set(FOOTBALLMATCH.GOALSHOME, match.score?.fullTime?.home ?: 0)
                .set(FOOTBALLMATCH.GOALSAWAY, match.score?.fullTime?.away ?: 0)
                .set(FOOTBALLMATCH.STATUS, dbStatus)
                .set(FOOTBALLMATCH.STARTAT, OffsetDateTime.parse(match.utcDate).toLocalDateTime())
                .execute()
        }
    }
}