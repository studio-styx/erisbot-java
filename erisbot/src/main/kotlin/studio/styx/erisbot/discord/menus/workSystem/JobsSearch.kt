package studio.styx.erisbot.discord.menus.workSystem

import net.dv8tion.jda.api.components.MessageTopLevelComponent
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.components.buttons.ButtonStyle
import shared.Colors
import shared.utils.Utils
import studio.styx.erisbot.generated.tables.records.CompanyRecord
import utils.ComponentBuilder
import java.util.List
import kotlin.math.ceil
import kotlin.math.min

class JobsSearch {
    fun jobsContainer(
        userId: String?,
        companys: MutableList<CompanyRecord>,
        page: Int
    ): MutableList<MessageTopLevelComponent?> {
        val container = ComponentBuilder.ContainerBuilder.Companion.create().withColor(Colors.DANGER)

        container.addText("## Centro de empregos\nVerifique abaixo os empregos disponiveis")
        container.addDivider(false)

        val jobsPerPage = 6
        val startIndex = (page - 1) * jobsPerPage
        val endIndex = min(startIndex + jobsPerPage, companys.size)

        var pageCompanys: MutableList<CompanyRecord>?

        if (startIndex >= companys.size) {
            pageCompanys = mutableListOf<CompanyRecord>()
        } else {
            pageCompanys = companys.subList(startIndex, endIndex)
        }

        for (company in pageCompanys) {
            val index = pageCompanys.indexOf(company)
            val replacements: MutableMap<String?, String?> = HashMap<String?, String?>()
            replacements.put("index", ((page - 1) * jobsPerPage + index + 1).toString())
            replacements.put("companyName", Utils.alternate<String?>(company.name, "Desconhecido").toString())
            replacements.put("difficulty", Utils.alternate(company.difficulty, "desconhecido").toString())
            replacements.put("description", Utils.alternate<String?>(company.description, "Sem descrição").toString())
            replacements.put("salary", Utils.formatNumber(company.wage!!.toDouble()))
            replacements.put("xp", Utils.formatNumber(company.experience!!.toDouble()))

            container.addSection(
                Button.primary("jobs/interview/" + company.id + "/" + userId, "Participar da Entrevista"),
                Utils.replaceText(
                    Utils.brBuilder(
                        "{index}. **{companyName}**",
                        "> **Dificuldade**: {difficulty}",
                        "> **Descrição**: {description}",
                        "> **Xp necessário**: {xp}",
                        "> **Salário**: {salary}"
                    ),
                    replacements as Map<String, String>
                )
            )
            if (index < pageCompanys.size - 1) {
                container.addDivider(false)
            }
        }

        val totalPages = ceil(companys.size.toDouble() / jobsPerPage).toInt()
        val firstPage = page <= 1
        val lastPage = page >= totalPages

        val buttons = ActionRow.of(
            Button.of(
                if (firstPage) ButtonStyle.SECONDARY else ButtonStyle.PRIMARY,
                "jobs/menu/page/" + (page - 1) + "/" + userId,
                "Voltar"
            ).withDisabled(firstPage),
            Button.of(
                if (lastPage) ButtonStyle.SECONDARY else ButtonStyle.PRIMARY,
                "jobs/menu/page/" + (page + 1) + "/" + userId,
                "Avançar"
            ).withDisabled(lastPage)
        )

        return List.of<MessageTopLevelComponent?>(container.build(), buttons)
    }
}