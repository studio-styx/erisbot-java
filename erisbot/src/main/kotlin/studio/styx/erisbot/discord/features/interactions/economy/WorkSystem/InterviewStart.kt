package studio.styx.erisbot.discord.features.interactions.economy.WorkSystem

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.genai.types.GenerateContentResponse
import database.utils.DatabaseUtils.getOrCreateUser
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionHook
import org.jooq.Configuration
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import services.gemini.GeminiRequest
import shared.Cache.set
import shared.Colors
import shared.utils.CustomIdHelper
import shared.utils.Utils.brBuilder
import shared.utils.Utils.replaceText
import studio.styx.erisbot.core.interfaces.ResponderInterface
import studio.styx.erisbot.generated.tables.records.CompanyRecord
import studio.styx.erisbot.generated.tables.references.COMPANY
import studio.styx.erisbot.discord.menus.workSystem.JobsSearch
import utils.ComponentBuilder.ContainerBuilder.Companion.create
import utils.ContainerRes
import java.math.BigDecimal
import java.util.Map
import java.util.function.Consumer
import java.util.stream.Collectors

@Component
class InterviewStart : ResponderInterface {
    @Autowired
    lateinit var dsl: DSLContext

    override fun getCustomId(): String {
        return "jobs/interview/:companyId/:userId"
    }

    override fun execute(event: ButtonInteractionEvent) {
        val customIdHelper = CustomIdHelper(getCustomId(), event.getCustomId())
        val res = ContainerRes()

        val companyId: Int = customIdHelper.getAsInt("companyId")!!
        val userId = customIdHelper.get("userId")

        if (userId != event.getUser().getId()) {
            event.deferReply(true).queue(Consumer { hook: InteractionHook ->
                val companys: MutableList<CompanyRecord?> = dsl.selectFrom(COMPANY)
                    .where(COMPANY.ISENABLED.eq(true))
                    .orderBy<Int?, Int?, BigDecimal?>(
                        COMPANY.EXPERIENCE.asc(),
                        COMPANY.DIFFICULTY.asc(),
                        COMPANY.WAGE.desc()
                    )
                    .fetch()
                val menuContext = JobsSearch()
                hook.editOriginalComponents(menuContext.jobsContainer(event.getUser().getId(),
                    companys as MutableList<CompanyRecord>, 1))
                    .useComponentsV2().queue()
            })
            return
        }

        event.deferEdit().queue(Consumer { hook: InteractionHook ->
            dsl.transaction { config: Configuration? ->
                val tx = config!!.dsl()
                val company = tx.selectFrom<CompanyRecord>(COMPANY)
                    .where(COMPANY.ID.eq(companyId))
                    .fetchOne()
                val userData = getOrCreateUser(tx, event.user.id)

                if (company == null) {
                    res.setColor(Colors.DANGER)
                        .setText("Eu não consegui encontrar essa empresa!")
                        .send(hook)
                    return@transaction
                }

                if (userData.contractid != null) {
                    res.setColor(Colors.DANGER)
                        .setText("Você já trabalha para uma empresa! use **/emprego demitir** para se demitir da empresa!")
                        .send(hook)
                    return@transaction
                }

                if (!company.isenabled!!) {
                    res.setColor(Colors.DANGER)
                        .setText("Essa empresa não está mais disponível!")
                        .send(hook)
                    return@transaction
                }

                if (userData.xp!! < company.experience!!) {
                    res.setColor(Colors.DANGER)
                        .setText("Você precisa ter: " + company.experience + " de xp para entrar nessa empresa, no momento você tem: " + userData.xp)
                        .send(hook)
                    return@transaction
                }

                hook.editOriginalComponents(
                    create()
                        .withColor(Colors.WARNING)
                        .addText("Aguarde enquanto o entrevistador chama a sua vez...")
                        .build()
                ).useComponentsV2().queue()

                val prompt = brBuilder(
                    "Você é um entrevistador de IA. Você irá entrevistar o candidato \"" + event.getUser()
                        .getName() + "\" para uma vaga na empresa \"" + company.name + "\".",
                    "Descrição da empresa: " + company.description,
                    if (company.flags!!.size > 0) "Flags da empresa (importante): " + company.flags.toString() else "",
                    "A empresa espera que seus funcionários tenham os seguintes valores e qualidades: " + company.expectations.toString(),
                    "A dificuldade da entrevista deve ser ajustada para ser mais fácil, pois o candidato possui a habilidade \"job_interview_easier\".",
                    "Gere exatamente 5 perguntas simples e relevantes para essa entrevista, levando em consideração o perfil da empresa e seus valores.",
                    "**Atenção:** as perguntas devem ser do tipo \"o que você faria\" e não \"o que você fez\", para manter a entrevista acessível.",
                    "Retorne **apenas** um array JSON **no formato exato**: [\"pergunta1\", \"pergunta2\", \"pergunta3\", \"pergunta4\", \"pergunta5\"]",
                    "Sem explicações ou texto adicional, apenas o array JSON."
                )

                val gemini: GeminiRequest = GeminiRequest()

                val result: GenerateContentResponse? = gemini.request(prompt)

                if (result == null) {
                    res.setColor(Colors.DANGER).setText("Não foi possivel obter as perguntas!").send(hook)
                    return@transaction
                }

                // formatar o resultado, retirando os "```" do inicio e do fim
                // Depois pegar as perguntas do array
                var text = result.text()

                if (text!!.startsWith("```") && text.endsWith("```")) {
                    text = text.substring(3, text.length - 3)
                }

                if (text.startsWith("json")) {
                    text = text.substring(4)
                }
                try {
                    val mapper = ObjectMapper()
                    val answers =
                        mapper.readValue<MutableList<String?>?>(text, object : TypeReference<MutableList<String?>>() {})

                    val interviewObjects = answers.stream()
                        .map<InterviewObject> { a: String? -> InterviewObject(a ?: "", null) }
                        .collect(Collectors.toList()) // Converta para List

                    set(
                        replaceText(
                            "interview:answers:{userId}",
                            Map.of<String, String>("userId", event.getUser().getId())
                        ), interviewObjects
                    ) // Salve a List, não o Stream

                    hook.editOriginalComponents(
                        create()
                            .withColor(Colors.PRIMARY)
                            .addText("# Pergunta 1")
                            .addDivider(false)
                            .addText(answers.get(0))
                            .addRow(
                                ActionRow.of(
                                    Button.primary(
                                        "interview/answer/0/${company.id}/${event.user.id}",
                                        "Responder"
                                    )
                                )
                            )
                            .build()
                    ).useComponentsV2().queue()
                } catch (e: Exception) {
                    res.setColor(Colors.DANGER).setText("Não foi possível processar as perguntas!").send(hook)
                    e.printStackTrace()
                    return@transaction
                }
            }
        })
    }
}