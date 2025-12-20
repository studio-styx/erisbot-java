package studio.styx.erisbot.discord.features.interactions.football.matches.menu

import database.extensions.football.football
import dev.minn.jda.ktx.coroutines.await
import menus.football.ExpectedMatchesValuesMenu
import menus.football.footballMatchesMenu
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import shared.utils.CustomIdHelper
import studio.styx.erisbot.core.interfaces.ResponderInterface
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

@Component
class FootballMatchesDate : ResponderInterface {
    @Autowired
    lateinit var dsl: DSLContext

    override val customId = "football/menu/date/:epochSecond"

    override suspend fun execute(event: ButtonInteractionEvent) {
        val params = CustomIdHelper(customId, event.customId)
        val epochSecond = params.getAsLong("epochSecond")!!

        // Usamos o fuso UTC para interpretar o timestamp do botão
        val zoneBr = ZoneId.of("America/Sao_Paulo")
        val referenceInBr = Instant.ofEpochSecond(epochSecond).atZone(zoneBr)
        val localDay = referenceInBr.toLocalDate()

        // 2. Criamos o intervalo de busca: Início e Fim do dia no Brasil
        // Convertemos para LocalDateTime para o Repository (jOOQ)
        val dateFrom = localDay.atStartOfDay() // 00:00:00
        val dateTo = localDay.atTime(23, 59, 59) // 23:59:59

        event.deferEdit().await()

        val matches = dsl.football.getMatchesWithTeamsAndLeaguesAsync(dateFrom, dateTo)

        val matchesFormatted = matches.map { m ->
            ExpectedMatchesValuesMenu(m.match, m.homeTeam, m.awayTeam, m.competition)
        }

        val menu = footballMatchesMenu(matchesFormatted, event.user.effectiveAvatarUrl, referenceInBr)
        event.hook.editOriginalComponents(menu).useComponentsV2().await()
    }
}