package studio.styx.erisbot.discord.features.interactions.football.matches.menu

import database.extensions.football.football
import dev.minn.jda.ktx.coroutines.await
import discord.extensions.jda.reply.rapidContainerReply
import menus.football.ExpectedMatchesValuesMenu
import menus.football.footballMatchesMenu
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.actionrow.ActionRowChildComponent
import net.dv8tion.jda.api.components.label.Label
import net.dv8tion.jda.api.components.selections.StringSelectMenu
import net.dv8tion.jda.api.components.textinput.TextInput
import net.dv8tion.jda.api.components.textinput.TextInputStyle
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.modals.Modal
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import shared.Colors
import studio.styx.erisbot.core.interfaces.ResponderInterface
import studio.styx.schemaEXtended.core.schemas.NumberSchema
import studio.styx.schemaEXtended.core.schemas.ObjectSchema
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@Component
class FootballMatchesOtherDate : ResponderInterface {
    @Autowired
    lateinit var dsl: DSLContext

    override val customId = "football/menu/other/otherData"

    override suspend fun execute(event: ButtonInteractionEvent) {
        val dayInput = TextInput.create("day", TextInputStyle.SHORT)
            .setPlaceholder("Ex: 21")
            .setMinLength(1)
            .setMaxLength(2)
            .setRequired(true)
            .build()

        val day = Label.of("Escolha um dia", dayInput)

        val monthSelect = StringSelectMenu.create("month")
            .setPlaceholder("Escolha o mês do ano")
            .addOption("Janeiro", "1")
            .addOption("Fevereiro", "2")
            .addOption("Março", "3")
            .addOption("Abril", "4")
            .addOption("Maio", "5")
            .addOption("Junho", "6")
            .addOption("Julho", "7")
            .addOption("Agosto", "8")
            .addOption("Setembro", "9")
            .addOption("Outubro", "10")
            .addOption("Novembro", "11")
            .addOption("Dezembro", "12")
            .build()

        val month = Label.of("Escolha um mês", monthSelect)

        val modal = Modal.create("football/menu/other/otherData", "Escolha uma data")
            .addComponents(day, month)
            .build()

        event.replyModal(modal).await()
    }

    companion object {
        const val dayError = "O dia do mês deve ser entre 1 a 31"
        const val monthError = "O mês deve ser entre o mês 1 ao mês 12"
        val SCHEMA = ObjectSchema()
            .addProperty("day", NumberSchema()
                .min(1)
                .max(31)
                .minError(dayError)
                .maxError(dayError)
                .parseError("Digite um dia do mês válido")
                .integer()
                .coerce())
            .addProperty("month", NumberSchema()
                .min(1)
                .max(12)
                .minError(monthError)
                .maxError(monthError)
                .parseError("Informe um mês válido")
                .integer()
                .coerce())
    }

    override suspend fun execute(event: ModalInteractionEvent) {
        // 1. Parsing inicial com seu SCHEMA (valida tipos e ranges básicos)
        val info = SCHEMA.parseOrThrow(mapOf(
            "day" to event.getValue("day")?.asString,
            "month" to event.getValue("month")?.asStringList?.firstOrNull() // getValues é mais estável para SelectMenus
        ))

        val day = info.getInteger("day")
        val month = info.getInteger("month")

        val zoneBr = ZoneId.of("America/Sao_Paulo")
        val currentYear = java.time.LocalDate.now(zoneBr).year

        val localDay = try {
            java.time.LocalDate.of(currentYear, month, day)
        } catch (_: java.time.DateTimeException) {
            event.rapidContainerReply(
                Colors.DANGER,
                "A data **$day/$month** não existe no calendário de $currentYear. Por favor, escolha um dia válido.",
                true
            )
            return
        }

        // 3. Preparação do intervalo de busca
        val dateFrom = localDay.atStartOfDay()
        val dateTo = localDay.atTime(23, 59, 59)

        event.deferEdit().await()

        val matches = dsl.football.getMatchesWithTeamsAndLeaguesAsync(dateFrom, dateTo)

        val matchesFormatted = matches.map { m ->
            ExpectedMatchesValuesMenu(m.match, m.homeTeam, m.awayTeam, m.competition)
        }

        // 4. Criação do menu com a data de referência correta (ZonedDateTime)
        val referenceInBr = localDay.atStartOfDay(zoneBr)
        val menu = footballMatchesMenu(matchesFormatted, event.user.effectiveAvatarUrl, referenceInBr)

        event.hook.editOriginalComponents(menu).useComponentsV2().await()
    }
}