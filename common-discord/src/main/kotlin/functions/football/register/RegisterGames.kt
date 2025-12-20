package functions.football.register

import database.extensions.transactionSuspend
import dtos.football.footballData.api.fixtureResult.common.Area
import dtos.football.footballData.api.fixtureResult.match.Match
import dtos.football.footballData.api.fixtureResult.match.MatchStatus
import dtos.football.footballData.api.fixtureResult.match.TeamSide
import dtos.football.footballData.api.fixtureResult.team.Player
import dtos.football.footballData.api.fixtureResult.team.PlayerPosition
import functions.football.ApiFootballDataSdk
import functions.football.handlers.HandleEndGame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import net.dv8tion.jda.api.JDA
import org.jooq.DSLContext
import org.jooq.impl.DSL
import studio.styx.erisbot.generated.enums.Matchstatus
import studio.styx.erisbot.generated.tables.references.FOOTBALLAREA
import studio.styx.erisbot.generated.tables.references.FOOTBALLLEAGUE
import studio.styx.erisbot.generated.tables.references.FOOTBALLMATCH
import studio.styx.erisbot.generated.tables.references.FOOTBALLPLAYER
import studio.styx.erisbot.generated.tables.references.FOOTBALLTEAM
import java.math.BigDecimal
import java.math.RoundingMode
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
    private val dsl: DSLContext,
    private val jda: JDA
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
        // 1. Buscamos IDs E PONTOS para calcular as odds
        val teamData = withContext(Dispatchers.IO) {
            val leagueId = dsl.select(FOOTBALLLEAGUE.ID)
                .from(FOOTBALLLEAGUE)
                .where(FOOTBALLLEAGUE.APIID.eq(match.competition.id))
                .fetchOne(FOOTBALLLEAGUE.ID)

            // Buscamos ID e POINTS do Home
            val homeRecord = dsl.select(FOOTBALLTEAM.ID, FOOTBALLTEAM.POINTS)
                .from(FOOTBALLTEAM)
                .where(FOOTBALLTEAM.APIID.eq(match.homeTeam.id))
                .fetchOne()

            // Buscamos ID e POINTS do Away
            val awayRecord = dsl.select(FOOTBALLTEAM.ID, FOOTBALLTEAM.POINTS)
                .from(FOOTBALLTEAM)
                .where(FOOTBALLTEAM.APIID.eq(match.awayTeam.id))
                .fetchOne()

            Triple(leagueId, homeRecord, awayRecord)
        }

        val (leagueDbId, homeRecord, awayRecord) = teamData

        // Evita crash se por algum motivo o time não tiver sido salvo (safety check)
        if (leagueDbId == null || homeRecord == null || awayRecord == null) return

        val homeDbId = homeRecord[FOOTBALLTEAM.ID]
        val awayDbId = awayRecord[FOOTBALLTEAM.ID]

        val (oddHome, oddDraw, oddAway) = calculateOdds(
            homeRecord[FOOTBALLTEAM.POINTS] ?: 0,
            awayRecord[FOOTBALLTEAM.POINTS] ?: 0
        )

        val matchBeforeUpdate = withContext(Dispatchers.IO) {
            dsl.selectFrom(FOOTBALLMATCH)
                .where(FOOTBALLMATCH.APIID.eq(match.id.toInt()))
                .fetchOne()
        }

        val newGame = dsl.transactionResultAsync { config ->
            val tx = config.dsl()

            val dbStatus = when(match.status) {
                MatchStatus.TIMED -> Matchstatus.SCHEDULED
                MatchStatus.CANCELLED -> Matchstatus.CANCELED
                else -> Matchstatus.valueOf(match.status.toString())
            }

            val matchDate = OffsetDateTime.parse(match.utcDate).toLocalDateTime()

            // Upsert da Partida com Lógica de Odds Condicional
            tx.insertInto(FOOTBALLMATCH)
                .set(FOOTBALLMATCH.APIID, match.id.toInt())
                .set(FOOTBALLMATCH.COMPETITIONID, leagueDbId)
                .set(FOOTBALLMATCH.HOMETEAMID, homeDbId)
                .set(FOOTBALLMATCH.AWAYTEAMID, awayDbId)
                .set(FOOTBALLMATCH.GOALSHOME, match.score?.fullTime?.home)
                .set(FOOTBALLMATCH.GOALSAWAY, match.score?.fullTime?.away)
                .set(FOOTBALLMATCH.STATUS, dbStatus)
                .set(FOOTBALLMATCH.STARTAT, matchDate)
                .set(FOOTBALLMATCH.ODDSHOMEWIN, oddHome)
                .set(FOOTBALLMATCH.ODDSDRAW, oddDraw)
                .set(FOOTBALLMATCH.ODDSAWAYWIN, oddAway)
                .onConflict(FOOTBALLMATCH.APIID)
                .doUpdate()
                .set(FOOTBALLMATCH.GOALSHOME, match.score?.fullTime?.home)
                .set(FOOTBALLMATCH.GOALSAWAY, match.score?.fullTime?.away)
                .set(FOOTBALLMATCH.STATUS, dbStatus)
                .set(FOOTBALLMATCH.STARTAT, matchDate)
                .set(
                    FOOTBALLMATCH.ODDSHOMEWIN,
                    DSL.case_()
                        .`when`(FOOTBALLMATCH.STARTAT.gt(LocalDateTime.now()), oddHome)
                        .else_(FOOTBALLMATCH.ODDSHOMEWIN)
                )
                .set(
                    FOOTBALLMATCH.ODDSDRAW,
                    DSL.case_()
                        .`when`(FOOTBALLMATCH.STARTAT.gt(LocalDateTime.now()), oddDraw)
                        .else_(FOOTBALLMATCH.ODDSDRAW)
                )
                .set(
                    FOOTBALLMATCH.ODDSAWAYWIN,
                    DSL.case_()
                        .`when`(FOOTBALLMATCH.STARTAT.gt(LocalDateTime.now()), oddAway)
                        .else_(FOOTBALLMATCH.ODDSAWAYWIN)
                )
                .returning()
                .fetchOne()
        }.await()

        if (matchBeforeUpdate != null) {
            if (match.status == MatchStatus.FINISHED) {
                if (matchBeforeUpdate.status != Matchstatus.FINISHED) {
                    val endGame = HandleEndGame(match, newGame!!, dsl, jda)
                    endGame.handle()
                }
            }
        }
    }

    private fun calculateOdds(homePoints: Int, awayPoints: Int): Triple<Double, Double, Double> {
        val totalPoints = (homePoints + awayPoints).coerceAtLeast(1).toDouble()

        // Função auxiliar para arredondar
        fun roundToTwoDecimals(value: Double): Double {
            return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .toDouble()
        }

        fun calcOdd(points: Int): Double {
            val strength = points / totalPoints
            // Inverter probabilidade
            val odd = 1 / strength.coerceAtLeast(0.05)
            // Limitar e arredondar
            return roundToTwoDecimals(odd.coerceIn(1.2, 8.0))
        }

        val oddHome = calcOdd(homePoints)
        val oddAway = calcOdd(awayPoints)

        // Cálculo do empate
        val diff = Math.abs(oddHome - oddAway)
        val rawDraw = (3.0 + (diff * 0.2)).coerceIn(2.5, 6.0)
        val oddDraw = roundToTwoDecimals(rawDraw)

        return Triple(oddHome, oddDraw, oddAway)
    }
}