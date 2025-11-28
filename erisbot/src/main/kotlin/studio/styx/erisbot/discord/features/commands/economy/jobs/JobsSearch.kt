package studio.styx.erisbot.discord.features.commands.economy.jobs

import database.utils.DatabaseUtils
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.DiscordLocale
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import shared.Colors
import studio.styx.erisbot.core.interfaces.CommandInterface
import studio.styx.erisbot.generated.enums.Contractstatus
import studio.styx.erisbot.generated.tables.records.CompanyRecord
import studio.styx.erisbot.generated.tables.references.COMPANY
import studio.styx.erisbot.generated.tables.references.CONTRACT
import studio.styx.erisbot.discord.menus.workSystem.JobsSearch
import utils.ComponentBuilder.ContainerBuilder.Companion.create
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.function.Consumer


@Component
class JobsSearch : CommandInterface {
    @Autowired
    lateinit var dsl: DSLContext

    override fun getSlashCommandData(): SlashCommandData {
        return Commands.slash("job", "💼 ✦ Search for a job or get dismiss")
            .addSubcommands(
                SubcommandData("search", "🔍 ✦ Search for a job")
                    .setNameLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "procurar")
                    .setDescriptionLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "🔍 ✦ Procurar por um emprego")
                    .setNameLocalization(DiscordLocale.SPANISH, "buscar")
                    .setDescriptionLocalization(DiscordLocale.SPANISH, "🔍 ✦ Buscar un trabajo")
                    .setNameLocalization(DiscordLocale.SPANISH_LATAM, "buscar")
                    .setDescriptionLocalization(DiscordLocale.SPANISH_LATAM, "🔍 ✦ Buscar un trabajo"),
                SubcommandData("dismiss", "🚪 ✦ Get dismiss from your job")
                    .setNameLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "demitir")
                    .setDescriptionLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "🚪 ✦ Ser demitido do seu emprego")
                    .setNameLocalization(DiscordLocale.SPANISH, "despedir")
                    .setDescriptionLocalization(DiscordLocale.SPANISH, "🚪 ✦ Ser despedido de tu trabajo")
                    .setNameLocalization(DiscordLocale.SPANISH_LATAM, "despedir")
                    .setDescriptionLocalization(DiscordLocale.SPANISH_LATAM, "🚪 ✦ Ser despedido de tu trabajo")
            )
            .setNameLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "emprego")
            .setDescriptionLocalization(
                DiscordLocale.PORTUGUESE_BRAZILIAN,
                "💼 ✦ Procure por um emprego ou seja demitido"
            )
            .setNameLocalization(DiscordLocale.SPANISH, "trabajo")
            .setDescriptionLocalization(DiscordLocale.SPANISH, "💼 ✦ Busca un trabajo o renuncia")
            .setNameLocalization(DiscordLocale.SPANISH_LATAM, "trabajo")
            .setDescriptionLocalization(DiscordLocale.SPANISH_LATAM, "💼 ✦ Busca un trabajo o renuncia")
            .setNameLocalization(DiscordLocale.ENGLISH_US, "job")
            .setDescriptionLocalization(DiscordLocale.ENGLISH_US, "💼 ✦ Search for a job or get dismiss")
    }

    override fun execute(event: SlashCommandInteractionEvent) {
        when (event.subcommandName) {
            "search" -> search(event)
            "dismiss" -> dismiss(event)
            else -> notFound(event)
        }
    }

    private fun search(event: SlashCommandInteractionEvent) {
        event.deferReply().queue(Consumer { hook: InteractionHook ->
            val companys: MutableList<CompanyRecord?> = dsl.selectFrom(COMPANY)
                .where(COMPANY.ISENABLED.eq(true))
                .orderBy<Int?, Int?, BigDecimal?>(
                    COMPANY.EXPERIENCE.asc(),
                    COMPANY.DIFFICULTY.asc(),
                    COMPANY.WAGE.desc()
                )
                .fetch()
            val menuContext = JobsSearch()
            hook.editOriginalComponents(menuContext.jobsContainer(event.user.id,
                companys as MutableList<CompanyRecord>, 1))
                .useComponentsV2().queue()
        })
    }

    private fun dismiss(event: SlashCommandInteractionEvent) {
        event.deferReply().queue(Consumer { hook: InteractionHook ->
            val user = DatabaseUtils.getOrCreateUser(dsl, event.user.id)
            val contractId = user.contractid

            if (contractId == null) {
                create()
                    .setEphemeral(true)
                    .withColor(Colors.DANGER)
                    .addText("Você não tem um emprego para se demitir!")
                    .reply(event)
                return@Consumer
            }

            user.contractid = null
            user.update()
            dsl.update(CONTRACT)
                .set<Contractstatus?>(CONTRACT.STATUS, Contractstatus.INACTIVE)
                .set<LocalDateTime?>(CONTRACT.UPDATEDAT, LocalDateTime.now())
                .execute()
            create()
                .setEphemeral(true)
                .withColor(Colors.SUCCESS)
                .addText("Você se demitiu do seu emprego com sucesso!")
                .reply(event)
        })
    }

    private fun notFound(event: SlashCommandInteractionEvent) {
        create()
            .withColor(Colors.DANGER)
            .setEphemeral(true)
            .addText("Esse comando não foi encontrado!")
            .reply(event)
    }
}
