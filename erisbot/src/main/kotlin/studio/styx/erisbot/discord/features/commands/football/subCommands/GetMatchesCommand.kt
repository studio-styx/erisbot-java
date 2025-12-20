package studio.styx.erisbot.discord.features.commands.football.subCommands

import dev.minn.jda.ktx.coroutines.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import menus.football.ExpectedMatchesValuesMenu
import menus.football.footballMatchesMenu
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import org.jooq.DSLContext
import studio.styx.erisbot.discord.features.commands.football.FootballCommands.MatchWithDetails
import studio.styx.erisbot.generated.tables.Footballleague
import studio.styx.erisbot.generated.tables.Footballteam
import studio.styx.erisbot.generated.tables.records.FootballleagueRecord
import studio.styx.erisbot.generated.tables.records.FootballmatchRecord
import studio.styx.erisbot.generated.tables.records.FootballteamRecord
import studio.styx.erisbot.generated.tables.references.FOOTBALLBET
import studio.styx.erisbot.generated.tables.references.FOOTBALLMATCH
import studio.styx.schemaEXtended.core.schemas.NumberSchema
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

class GetMatchesCommand(
    private val event: SlashCommandInteractionEvent,
    private val dsl: DSLContext
) {
    companion object {
        private val MATCH_SCHEMA = NumberSchema()
            .min(1)
            .minError("O id da partida deve ser maior que 1")
            .parseError("O id da partida deve ser um número")
            .coerce()
    }
    suspend fun execute() {
        val matchIdInput = event.getOption("match")?.asString

        event.deferReply().await()

        if (matchIdInput != null) {
            val matchId = MATCH_SCHEMA.parseOrThrow(matchIdInput)
            TODO("Get a determined match is not implemented yet")
        }

        val nowUtc = Instant.now()
        val atStartOfDayBrazilTime = nowUtc.atOffset(ZoneOffset.of("America/São_Paulo")).toLocalDate().atStartOfDay()
        val atEndOfDayBrazilTime = nowUtc.atOffset(ZoneOffset.of("America/São_Paulo")).toLocalDate().atTime(23, 59)

        val matches = getMatches(atStartOfDayBrazilTime, atEndOfDayBrazilTime)
        val matchesFormatted = matches.map { m ->
            ExpectedMatchesValuesMenu(
                match = m.match,
                homeTeam = m.homeTeam,
                awayTeam = m.awayTeam,
                competition = m.competition
            )
        }
        val menu = footballMatchesMenu(
            matchesFormatted, event.user.effectiveAvatarUrl, ZonedDateTime.of(nowUtc.atOffset(ZoneOffset.UTC).toLocalDateTime(),
                ZoneOffset.UTC)
        )

        event.hook.editOriginalComponents(menu).useComponentsV2().await()
    }

    private suspend fun getMatches(dateFrom: LocalDateTime, dateTo: LocalDateTime): List<MatchWithDetails> {
        val homeTeam = Footballteam("home_team")
        val awayTeam = Footballteam("away_team")
        val league = Footballleague("league")

        return withContext(Dispatchers.IO) {
            dsl.select(
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
                .take(25)
        }
    }

    private data class MatchWithDetails(
        val match: FootballmatchRecord,
        val homeTeam: FootballteamRecord,
        val awayTeam: FootballteamRecord,
        val competition: FootballleagueRecord,
    )
}