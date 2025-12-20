package database.repository.football

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.DSLContext
import studio.styx.erisbot.generated.tables.records.FootballleagueRecord
import studio.styx.erisbot.generated.tables.records.FootballmatchRecord
import studio.styx.erisbot.generated.tables.records.FootballteamRecord
import studio.styx.erisbot.generated.tables.references.FOOTBALLLEAGUE
import studio.styx.erisbot.generated.tables.references.FOOTBALLMATCH
import studio.styx.erisbot.generated.tables.references.FOOTBALLTEAM
import java.time.LocalDateTime

data class MatchWithDetails(
    val match: FootballmatchRecord,
    val homeTeam: FootballteamRecord,
    val awayTeam: FootballteamRecord,
    val competition: FootballleagueRecord,
)

class FootballRepository(private val dsl: DSLContext) {
    fun getMatchWithTeamsAndLeague(matchId: Long): MatchWithDetails? {
        val homeTeam = FOOTBALLTEAM.`as`("home_team")
        val awayTeam = FOOTBALLTEAM.`as`("away_team")
        val league = FOOTBALLLEAGUE.`as`("league")

        return dsl.select(
            FOOTBALLMATCH.asterisk(),
            homeTeam.asterisk(),
            awayTeam.asterisk(),
            league.asterisk(),
        )
            .from(FOOTBALLMATCH)
            .innerJoin(homeTeam).on(FOOTBALLMATCH.HOMETEAMID.eq(homeTeam.ID))
            .innerJoin(awayTeam).on(FOOTBALLMATCH.AWAYTEAMID.eq(awayTeam.ID))
            .innerJoin(league).on(FOOTBALLMATCH.COMPETITIONID.eq(league.ID))
            .where(FOOTBALLMATCH.ID.eq(matchId))
            .orderBy(
                FOOTBALLMATCH.STARTAT.asc(),
            )
            .fetchOne { record ->
                MatchWithDetails(
                    match = record.into(FOOTBALLMATCH),
                    homeTeam = record.into(homeTeam),
                    awayTeam = record.into(awayTeam),
                    competition = record.into(league),
                )
            }
    }

    suspend fun getMatchWithTeamsAndLeagueAsync(matchId: Long): MatchWithDetails? {
        return withContext(Dispatchers.IO) {
            getMatchWithTeamsAndLeague(matchId)
        }
    }

    fun getMatchesWithTeamsAndLeagues(dateFrom: LocalDateTime, dateTo: LocalDateTime): List<MatchWithDetails> {
        val homeTeam = FOOTBALLTEAM.`as`("home_team")
        val awayTeam = FOOTBALLTEAM.`as`("away_team")
        val league = FOOTBALLLEAGUE.`as`("league")

        return dsl.select(
            FOOTBALLMATCH.asterisk(),
            homeTeam.asterisk(),
            awayTeam.asterisk(),
            league.asterisk(),
        )
            .from(FOOTBALLMATCH)
            .innerJoin(homeTeam).on(FOOTBALLMATCH.HOMETEAMID.eq(homeTeam.ID))
            .innerJoin(awayTeam).on(FOOTBALLMATCH.AWAYTEAMID.eq(awayTeam.ID))
            .innerJoin(league).on(FOOTBALLMATCH.COMPETITIONID.eq(league.ID))
            .where(
                FOOTBALLMATCH.STARTAT.between(dateFrom, dateTo)
            )
            .orderBy(
                FOOTBALLMATCH.STARTAT.asc(),
            )
            .fetch()
            .map { record ->
                MatchWithDetails(
                    match = record.into(FOOTBALLMATCH),
                    homeTeam = record.into(homeTeam),
                    awayTeam = record.into(awayTeam),
                    competition = record.into(league),
                )
            }
    }

    suspend fun getMatchesWithTeamsAndLeaguesAsync(dateFrom: LocalDateTime, dateTo: LocalDateTime): List<MatchWithDetails> {
        return withContext(Dispatchers.IO) {
            getMatchesWithTeamsAndLeagues(dateFrom, dateTo)
        }
    }
}