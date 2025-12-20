package menus.football

import net.dv8tion.jda.api.components.MessageTopLevelComponent
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import shared.Colors
import shared.utils.DiscordTimeStyle
import shared.utils.Icon
import shared.utils.Utils
import studio.styx.erisbot.generated.enums.Matchstatus
import studio.styx.erisbot.generated.tables.records.FootballleagueRecord
import studio.styx.erisbot.generated.tables.records.FootballmatchRecord
import studio.styx.erisbot.generated.tables.records.FootballteamRecord
import utils.ComponentBuilder
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.*

data class ExpectedMatchesValuesMenu(
    val match: FootballmatchRecord,
    val homeTeam: FootballteamRecord,
    val awayTeam: FootballteamRecord,
    val competition: FootballleagueRecord
)

object FootballMenuHelper {
    val BRAZIL_ZONE = ZoneId.of("America/Sao_Paulo")
    val BRAZIL_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        .withLocale(Locale("pt", "BR"))

    fun formatDateRelativeToBrazil(date: ZonedDateTime): String {
        // Garantimos que a comparação é feita no fuso do Brasil
        val dateInBr = date.withZoneSameInstant(BRAZIL_ZONE)
        val today = ZonedDateTime.now(BRAZIL_ZONE).toLocalDate()

        return when (dateInBr.toLocalDate()) {
            today -> "hoje"
            today.minusDays(1) -> "ontem"
            today.plusDays(1) -> "amanhã"
            else -> dateInBr.format(BRAZIL_DATE_FORMATTER)
        }
    }
}

fun footballMatchesMenu(
    matches: List<ExpectedMatchesValuesMenu>,
    defaultImageUrl: String,
    referenceDate: ZonedDateTime,
    page: Int = 0
): MutableList<MessageTopLevelComponent> {
    val formattedDate = FootballMenuHelper.formatDateRelativeToBrazil(referenceDate)
    val container = ComponentBuilder.ContainerBuilder.create()
        .withColor(Colors.SUCCESS)

    // Configurações
    val matchesPerPage = 5
    val totalPages = if (matches.isEmpty()) 1 else ((matches.size - 1) / matchesPerPage) + 1
    val currentPage = page.coerceIn(0, totalPages - 1)

    // Converter UTC para horário brasileiro para exibição
    val tomorrowBr = referenceDate.plusDays(1)
    val yesterdayBr = referenceDate.minusDays(1)

    // Título com a data formatada
    container.addText("## Partidas de futebol de $formattedDate")
    container.addDivider()

    // Paginação
    val startIdx = currentPage * matchesPerPage
    val endIdx = minOf(startIdx + matchesPerPage, matches.size)
    val paginatedMatches = if (matches.isNotEmpty()) {
        matches.subList(startIdx, endIdx)
    } else {
        emptyList()
    }

    if (paginatedMatches.isNotEmpty()) {
        paginatedMatches.forEach { matchData ->
            val match = matchData.match
            val homeTeam = matchData.homeTeam
            val awayTeam = matchData.awayTeam
            val competition = matchData.competition

            // Converter horário do jogo para Brasil
            val matchStartUtc = match.startat!!.atZone(ZoneOffset.UTC)
            val matchStartBrazil = match.startat!!.atZone(FootballMenuHelper.BRAZIL_ZONE)
            val nowInBrazil = ZonedDateTime.now(FootballMenuHelper.BRAZIL_ZONE)

            val matchStatus = when(match.status) {
                Matchstatus.FINISHED -> "Finalizado"
                Matchstatus.SCHEDULED -> "Agendado"
                Matchstatus.CANCELED -> "Cancelado"
                Matchstatus.POSTPONED -> "Reagendado"
                Matchstatus.IN_PLAY, Matchstatus.LIVE -> "Em andamento"
                Matchstatus.PAUSED -> "Pausado"
                else -> "Desconhecido"
            }

            // Construir texto do jogo
            val matchText = Utils.brBuilder(
                "## ${Icon.static.get("trophy")} - ${competition.name}",
                "${Icon.static.get("soccer_field")} - **${homeTeam.name}** ${match.goalshome ?: ""} x ${match.goalsaway ?: ""} **${awayTeam.name}**",
                "${Icon.static.get("stadium")} - **Estádio:** ${match.venue ?: "Desconhecido"}",
                if (matchStartBrazil.isBefore(nowInBrazil)) {
                    "${Icon.static.get("alarm")} - **Começou:** ${Utils.formatDiscordTime(match.startat!!, DiscordTimeStyle.RELATIVE)} | ${Utils.formatDiscordTime(match.startat!!, DiscordTimeStyle.LONGDATETIME)}"
                } else {
                    "${Icon.static.get("alarm")} - **Começa:** ${Utils.formatDiscordTime(match.startat!!, DiscordTimeStyle.RELATIVE)} | ${Utils.formatDiscordTime(match.startat!!, DiscordTimeStyle.LONGDATETIME)}"
                },
                "**Status:** $matchStatus",
                "**Odd para o time da casa:** ${match.oddshomewin ?: "Desconhecido"}",
                "**Odd para o empate:** ${match.oddsdraw ?: "Desconhecido"}",
                "**Odd para o time visitante:** ${match.oddsawaywin ?: "Desconhecido"}"
            )

            // Imagem do emblema (competição > time da casa > time visitante > padrão)
            val emblemUrl = competition.emblem ?: homeTeam.crest ?: awayTeam.crest ?: defaultImageUrl

            container.addSection(emblemUrl, matchText)
                .addRow(
                    ActionRow.of(
                        Button.primary(
                            "football/match/view/${match.id}",
                            "Mais informações"
                        )
                    )
                )
                .addDivider()
        }
    } else {
        container.addText("Nenhum jogo agendado para esse dia")
    }

    val tomorrowTimestamp = tomorrowBr.withHour(12).toInstant().epochSecond
    val yesterdayTimestamp = yesterdayBr.withHour(12).toInstant().epochSecond

    // Botões de paginação (Mantidos iguais, usando a data atual do menu)
    val currentTimestamp = referenceDate.toInstant().epochSecond
    val paginationButtons = ActionRow.of(
        Button.primary("football/menu/page/${page - 1}/$currentTimestamp", "Voltar")
            .withDisabled(page == 0),
        Button.primary("football/menu/page/${page + 1}/$currentTimestamp", "Avançar")
            .withDisabled(page >= totalPages - 1 || totalPages == 0)
    )

    val dateButtons = ActionRow.of(
        Button.primary("football/menu/date/$yesterdayTimestamp",
            yesterdayBr.format(FootballMenuHelper.BRAZIL_DATE_FORMATTER)),
        Button.primary("football/menu/date/$tomorrowTimestamp",
            tomorrowBr.format(FootballMenuHelper.BRAZIL_DATE_FORMATTER)),
        Button.secondary("football/menu/other/otherData", "Escolher uma data")
    )

    container.addRow(paginationButtons)
    val containerContent = container.build()

    return mutableListOf(containerContent, dateButtons)
}
