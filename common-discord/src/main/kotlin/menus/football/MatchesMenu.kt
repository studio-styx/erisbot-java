package menus.football

import net.dv8tion.jda.api.components.MessageTopLevelComponent
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import shared.utils.DiscordTimeStyle
import shared.utils.Icon
import shared.utils.Utils
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
    // Zona do Brasil (considera horário de Brasília - America/Sao_Paulo)
    val BRAZIL_ZONE = ZoneId.of("America/Sao_Paulo")

    // Formatter para datas em português do Brasil
    val BRAZIL_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        .withLocale(Locale("pt", "BR"))

    /**
     * Formata a data em termos relativos (hoje, ontem, amanhã, etc.)
     * em relação ao horário brasileiro atual
     */
    fun formatDateRelativeToBrazil(date: ZonedDateTime): String {
        val nowInBrazil = ZonedDateTime.now(BRAZIL_ZONE)

        // Converter para data pura (sem horas) para comparação
        val today = nowInBrazil.toLocalDate()
        val yesterday = today.minusDays(1)
        val dayBeforeYesterday = today.minusDays(2)
        val tomorrow = today.plusDays(1)
        val dayAfterTomorrow = today.plusDays(2)

        val targetDate = date.withZoneSameInstant(BRAZIL_ZONE).toLocalDate()

        return when (targetDate) {
            today -> "hoje"
            yesterday -> "ontem"
            dayBeforeYesterday -> "anteontem"
            tomorrow -> "amanhã"
            dayAfterTomorrow -> "depois de amanhã"
            else -> targetDate.format(BRAZIL_DATE_FORMATTER)
        }
    }

    /**
     * Converte UTC para horário brasileiro
     */
    fun utcToBrazilTime(utcTime: ZonedDateTime): ZonedDateTime {
        return utcTime.withZoneSameInstant(BRAZIL_ZONE)
    }
}

fun footballMatchesMenu(
    matches: List<ExpectedMatchesValuesMenu>,
    defaultImageUrl: String,
    utcDate: ZonedDateTime, // Data em UTC
    page: Int = 0
): MutableList<MessageTopLevelComponent> {
    val container = ComponentBuilder.ContainerBuilder.create()

    // Configurações
    val matchesPerPage = 5
    val totalPages = if (matches.isEmpty()) 1 else ((matches.size - 1) / matchesPerPage) + 1
    val currentPage = page.coerceIn(0, totalPages - 1)

    // Converter UTC para horário brasileiro para exibição
    val brazilDate = FootballMenuHelper.utcToBrazilTime(utcDate)
    val formattedDate = FootballMenuHelper.formatDateRelativeToBrazil(brazilDate)

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
            val matchStartBrazil = FootballMenuHelper.utcToBrazilTime(matchStartUtc)
            val nowInBrazil = ZonedDateTime.now(FootballMenuHelper.BRAZIL_ZONE)

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

    // Cálculos de datas para navegação
    val tomorrowUtc = utcDate.plusDays(1)
        .toLocalDate()
        .atStartOfDay(ZoneOffset.UTC)
    val yesterdayUtc = utcDate.minusDays(1)
        .toLocalDate()
        .atStartOfDay(ZoneOffset.UTC)

    // Converter para Brasil para exibição dos botões
    val tomorrowBrazil = FootballMenuHelper.utcToBrazilTime(tomorrowUtc)
    val yesterdayBrazil = FootballMenuHelper.utcToBrazilTime(yesterdayUtc)

    // Botões de paginação
    val paginationButtons = ActionRow.of(
        Button.primary(
            "football/menu/page/${currentPage - 1}/${utcDate.toInstant().epochSecond}",
            "Voltar"
        ).withDisabled(currentPage == 0),
        Button.primary(
            "football/menu/page/${currentPage + 1}/${utcDate.toInstant().epochSecond}",
            "Avançar"
        ).withDisabled(currentPage >= totalPages - 1 || totalPages == 0)
    )

    // Botões de navegação por data
    val dateButtons = ActionRow.of(
        Button.primary(
            "football/menu/date/${yesterdayUtc.toInstant().epochSecond}",
            yesterdayBrazil.format(FootballMenuHelper.BRAZIL_DATE_FORMATTER)
        ),
        Button.primary(
            "football/menu/date/${tomorrowUtc.toInstant().epochSecond}",
            tomorrowBrazil.format(FootballMenuHelper.BRAZIL_DATE_FORMATTER)
        ),
        Button.secondary(
            "football/menu/other/otherData",
            "Escolher uma data"
        )
    )

    container.addRow(paginationButtons)
    val containerContent = container.build()

    return mutableListOf(containerContent, dateButtons)
}
