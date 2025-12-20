package studio.styx.erisbot.discord.features.commands.football.subCommands

import database.extensions.football.football
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
import studio.styx.erisbot.generated.tables.references.FOOTBALLLEAGUE
import studio.styx.erisbot.generated.tables.references.FOOTBALLMATCH
import studio.styx.erisbot.generated.tables.references.FOOTBALLTEAM
import studio.styx.schemaEXtended.core.schemas.NumberSchema
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
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

        // CORREÇÃO 1: Usar ZoneId em vez de ZoneOffset
        val brazilZone = ZoneId.of("America/Sao_Paulo")

        // Converte o Instant atual para o ZonedDateTime do Brasil
        val zonedDateTimeBrazil = nowUtc.atZone(brazilZone)

        // Pega o início e o fim do dia
        val atStartOfDayBrazilTime = zonedDateTimeBrazil.toLocalDate().atStartOfDay()
        val atEndOfDayBrazilTime = zonedDateTimeBrazil.toLocalDate().atTime(23, 59, 59)

        val matches = dsl.football.getMatchesWithTeamsAndLeaguesAsync(atStartOfDayBrazilTime, atEndOfDayBrazilTime)

        val matchesFormatted = matches.map { m ->
            ExpectedMatchesValuesMenu(
                match = m.match,
                homeTeam = m.homeTeam,
                awayTeam = m.awayTeam,
                competition = m.competition
            )
        }

        val menu = footballMatchesMenu(
            matchesFormatted,
            event.user.effectiveAvatarUrl,
            ZonedDateTime.of(nowUtc.atZone(ZoneOffset.UTC).toLocalDateTime(), ZoneOffset.UTC)
        )

        event.hook.editOriginalComponents(menu).useComponentsV2().await()
    }

    private data class MatchWithDetails(
        val match: FootballmatchRecord,
        val homeTeam: FootballteamRecord,
        val awayTeam: FootballteamRecord,
        val competition: FootballleagueRecord,
    )
}