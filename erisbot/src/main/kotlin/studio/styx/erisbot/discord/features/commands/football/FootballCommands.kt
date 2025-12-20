package studio.styx.erisbot.discord.features.commands.football

import dev.minn.jda.ktx.coroutines.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import menus.football.FootballMenuHelper
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import shared.utils.Utils
import studio.styx.erisbot.core.interfaces.CommandInterface
import studio.styx.erisbot.discord.features.commands.football.subCommands.GetMatchesCommand
import studio.styx.erisbot.discord.features.commands.football.subCommands.SetFavoriteTeamCommand
import studio.styx.erisbot.generated.tables.Footballleague
import studio.styx.erisbot.generated.tables.Footballteam
import studio.styx.erisbot.generated.tables.records.FootballleagueRecord
import studio.styx.erisbot.generated.tables.records.FootballmatchRecord
import studio.styx.erisbot.generated.tables.records.FootballteamRecord
import studio.styx.erisbot.generated.tables.references.FOOTBALLBET
import studio.styx.erisbot.generated.tables.references.FOOTBALLMATCH
import studio.styx.erisbot.generated.tables.references.FOOTBALLTEAM
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

@Component
class FootballCommands : CommandInterface {
    @Autowired
    lateinit var dsl: DSLContext

    override fun getSlashCommandData(): SlashCommandData {
        val subCommands = listOf(
            SubcommandData("matches", "partidas de futebol")
                .addOption(OptionType.STRING, "match", "partida para ver informações", false, true),
            SubcommandData("bets", "ver suas apostas"),
            SubcommandData("favorite_team", "defina o time que você torce")
                .addOption(OptionType.STRING, "team", "nome do time", true, true),
        )

        return Commands.slash("football", "comandos de futebol")
            .addSubcommands(subCommands)
    }

    override suspend fun onAutoComplete(event: CommandAutoCompleteInteractionEvent) {
        val focused = event.focusedOption
        val focusedValue = focused.value

        when (focused.name) {
            "match" -> {
                val homeTeam = Footballteam("home_team")
                val awayTeam = Footballteam("away_team")
                val league = Footballleague("league")

                val now = Instant.now()
                val in3Days = now.plus(3, ChronoUnit.DAYS)

                val atStartOfDayUtc = LocalDateTime.ofInstant(now, ZoneOffset.UTC)
                    .withHour(0).withMinute(0).withSecond(0).withNano(0)
                val in3DaysUtc = LocalDateTime.ofInstant(in3Days, ZoneOffset.UTC)
                    .withHour(23).withMinute(59).withSecond(59).withNano(0)

                val betCountField = dsl.selectCount()
                    .from(FOOTBALLBET)
                    .where(FOOTBALLBET.MATCHID.eq(FOOTBALLMATCH.ID))
                    .asField<Int>("bet_count")

                val totalGoalsField = FOOTBALLMATCH.GOALSHOME.plus(FOOTBALLMATCH.GOALSAWAY).`as`("total_goals")

                val totalPointsField = homeTeam.POINTS.plus(awayTeam.POINTS).`as`("total_team_points")

                val results = withContext(Dispatchers.IO) {
                    dsl.select(
                        FOOTBALLMATCH.asterisk(),
                        homeTeam.asterisk(),
                        awayTeam.asterisk(),
                        league.asterisk(),
                        betCountField,
                        totalGoalsField
                    )
                        .from(FOOTBALLMATCH)
                        .innerJoin(homeTeam).on(FOOTBALLMATCH.HOMETEAMID.eq(homeTeam.ID))
                        .innerJoin(awayTeam).on(FOOTBALLMATCH.AWAYTEAMID.eq(awayTeam.ID))
                        .innerJoin(league).on(FOOTBALLMATCH.COMPETITIONID.eq(league.ID))
                        .where(
                            FOOTBALLMATCH.STARTAT.between(atStartOfDayUtc, in3DaysUtc)
                                .and(
                                    homeTeam.NAME.containsIgnoreCase(focusedValue)
                                        .or(awayTeam.NAME.containsIgnoreCase(focusedValue))
                                        .or(league.NAME.containsIgnoreCase(focusedValue))
                                )
                        )
                        .orderBy(
                            betCountField.desc(),
                            FOOTBALLMATCH.STARTAT.asc(),
                            totalGoalsField.desc(),
                            totalPointsField.desc()
                        )
                        .fetch()
                        .map { record ->
                            MatchWithDetails(
                                match = record.into(FOOTBALLMATCH),
                                homeTeam = record.into(homeTeam),
                                awayTeam = record.into(awayTeam),
                                competition = record.into(league),
                                betCount = record.get(betCountField) ?: 0,
                                totalGoals = record.get(totalGoalsField) ?: 0
                            )
                        }
                        .take(25)
                }

                if (results.isEmpty()) {
                    event.replyChoice("Nenhuma partida encontrada", "null")
                    return
                }

                val choices = results.map { m ->
                    net.dv8tion.jda.api.interactions.commands.Command.Choice(
                        Utils.limitText(
                            "${m.homeTeam.name} x ${m.awayTeam.name} || ${m.competition.name} ||| ${m.match.startat!!.atZone(ZoneId.of("America/Sao_Paulo")).format(
                                FootballMenuHelper.BRAZIL_DATE_FORMATTER)}",
                            97, "..."
                        ),
                        m.match.id.toString()
                    )
                }

                event.replyChoices(choices).await()
            }
            "team" -> {
                val teams = withContext(Dispatchers.IO) {
                    dsl.selectFrom(FOOTBALLTEAM)
                        .where(FOOTBALLTEAM.NAME.contains(focusedValue))
                        .fetch()
                        .take(25)
                }

                if (teams.isEmpty()) {
                    event.replyChoice("Nenhum time encontrado", "null")
                    return
                }

                val choices = teams.map { t ->
                    net.dv8tion.jda.api.interactions.commands.Command.Choice(
                        t.name!!, t.id.toString()
                    )
                }

                event.replyChoices(choices).await()
            }
        }
    }

    private data class MatchWithDetails(
        val match: FootballmatchRecord,
        val homeTeam: FootballteamRecord,
        val awayTeam: FootballteamRecord,
        val competition: FootballleagueRecord,
        val betCount: Int,
        val totalGoals: Int
    )

    override suspend fun execute(event: SlashCommandInteractionEvent) {
        when (event.subcommandName) {
            "favorite_team" -> SetFavoriteTeamCommand(event, dsl).execute()
            "matches" -> GetMatchesCommand(event, dsl).execute()
        }
    }
}