package studio.styx.erisbot.features.interactions.economy.workSystem;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.types.GenerateContentResponse;
import com.google.gson.Gson;
import database.utils.DatabaseUtils;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import shared.Cache;
import shared.Colors;
import shared.utils.CustomIdHelper;
import shared.utils.Utils;
import studio.styx.erisbot.core.ResponderInterface;
import studio.styx.erisbot.generated.tables.records.CompanyRecord;
import studio.styx.erisbot.generated.tables.records.UserRecord;
import studio.styx.erisbot.generated.tables.references.TablesKt;
import studio.styx.erisbot.menus.economy.workSystem.JobsSearch;
import studio.styx.erisbot.services.gemini.GeminiRequest;
import utils.ComponentBuilder;
import utils.ContainerRes;
import studio.styx.sx.sx;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class InterviewStart implements ResponderInterface {
    @Autowired
    private DSLContext dsl;

    @Override
    public String getCustomId() {
        return "jobs/interview/:companyId/:userId";
    }

    @Override
    public void execute(ButtonInteractionEvent event) {
        CustomIdHelper customIdHelper = new CustomIdHelper(getCustomId(), event.getCustomId());
        ContainerRes res = new ContainerRes();

        int companyId = customIdHelper.getAsInt("companyId");
        String userId = customIdHelper.get("userId");

        if (!userId.equals(event.getUser().getId())) {
            event.deferReply(true).queue(hook -> {
                List<CompanyRecord> companys = dsl.selectFrom(TablesKt.getCOMPANY())
                        .where(TablesKt.getCOMPANY().getISENABLED().eq(true))
                        .orderBy(
                                TablesKt.getCOMPANY().getEXPERIENCE().asc(),
                                TablesKt.getCOMPANY().getDIFFICULTY().asc(),
                                TablesKt.getCOMPANY().getWAGE().desc()
                        )
                        .fetch();

                JobsSearch menuContext = new JobsSearch();

                hook.editOriginalComponents(menuContext.jobsContainer(event.getUser().getId(), companys, 1)).useComponentsV2().queue();
            });
            return;
        }

        event.deferEdit().queue(hook -> {
            dsl.transaction(config -> {
                DSLContext tx = config.dsl();

                CompanyRecord company = tx.selectFrom(TablesKt.getCOMPANY())
                        .where(TablesKt.getCOMPANY().getID().eq(companyId))
                        .fetchOne();
                UserRecord userData = DatabaseUtils.getOrCreateUser(tx, event.getUser().getId());

                if (company == null) {
                    res.setColor(Colors.DANGER)
                            .setText("Eu não consegui encontrar essa empresa!")
                            .send(hook);
                    return;
                }

                if (userData.getContractid() != null) {
                    res.setColor(Colors.DANGER)
                            .setText("Você já trabalha para uma empresa! use **/emprego demitir** para se demitir da empresa!")
                            .send(hook);
                    return;
                }

                if (!company.getIsenabled()) {
                    res.setColor(Colors.DANGER)
                            .setText("Essa empresa não está mais disponível!")
                            .send(hook);
                    return;
                }

                if (userData.getXp() < company.getExperience()) {
                    res.setColor(Colors.DANGER)
                            .setText("Você precisa ter: " + company.getExperience() + " de xp para entrar nessa empresa, no momento você tem: " + userData.getXp())
                            .send(hook);
                    return;
                }

                hook.editOriginalComponents(
                        ComponentBuilder.ContainerBuilder.create()
                                .withColor(Colors.WARNING)
                                .addText("Aguarde enquanto o entrevistador chama a sua vez...")
                                .build()
                ).useComponentsV2().queue();

                String prompt = Utils.brBuilder(
                        "Você é um entrevistador de IA. Você irá entrevistar o candidato \"" + event.getUser().getName() + "\" para uma vaga na empresa \"" + company.getName() + "\".",
                        "Descrição da empresa: " + company.getDescription(),
                        company.getFlags().length > 0 ? "Flags da empresa (importante): " + company.getFlags().toString() : "",
                        "A empresa espera que seus funcionários tenham os seguintes valores e qualidades: " + company.getExpectations().toString(),
                        "A dificuldade da entrevista deve ser ajustada para ser mais fácil, pois o candidato possui a habilidade \"job_interview_easier\".",
                        "Gere exatamente 5 perguntas simples e relevantes para essa entrevista, levando em consideração o perfil da empresa e seus valores.",
                        "**Atenção:** as perguntas devem ser do tipo \"o que você faria\" e não \"o que você fez\", para manter a entrevista acessível.",
                        "Retorne **apenas** um array JSON **no formato exato**: [\"pergunta1\", \"pergunta2\", \"pergunta3\", \"pergunta4\", \"pergunta5\"]",
                        "Sem explicações ou texto adicional, apenas o array JSON."
                );

                GeminiRequest gemini = new GeminiRequest();

                GenerateContentResponse result = gemini.request(prompt);

                if (result == null) {
                    res.setColor(Colors.DANGER).setText("Não foi possivel obter as perguntas!").send(hook);
                    return;
                }

                // formatar o resultado, retirando os "```" do inicio e do fim
                // Depois pegar as perguntas do array

                String text = result.text();

                if (text.startsWith("```") && text.endsWith("```")) {
                    text = text.substring(3, text.length() - 3);
                }

                if (text.startsWith("json")) {
                    text = text.substring(4);
                }

                try {
                    ObjectMapper mapper = new ObjectMapper();
                    List<String> answers = mapper.readValue(text, new TypeReference<List<String>>() {});

                    // CORREÇÃO: Converta o Stream para List antes de salvar no cache
                    List<InterviewObject> interviewObjects = answers.stream()
                            .map(a -> new InterviewObject().setAnswer(a))
                            .collect(Collectors.toList()); // Converta para List

                    Cache.set(Utils.replaceText(
                            "interview:answers:{userId}",
                            Map.of("userId", event.getUser().getId())
                    ), interviewObjects); // Salve a List, não o Stream

                    hook.editOriginalComponents(
                            ComponentBuilder.ContainerBuilder.create()
                                    .withColor(Colors.PRIMARY)
                                    .addText("# Pergunta 1")
                                    .addDivider(false)
                                    .addText(answers.get(0))
                                    .addRow(ActionRow.of(
                                            Button.primary("interview/answer/" + 0 + "/" + company.getId() + "/" + event.getUser().getId(), "Responder")
                                    ))
                                    .build()
                    ).useComponentsV2().queue();

                } catch (Exception e) {
                    res.setColor(Colors.DANGER).setText("Não foi possível processar as perguntas!").send(hook);
                    e.printStackTrace();
                    return;
                }
            });
        });
    }
}
