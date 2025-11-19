package studio.styx.erisbot.features.interactions.economy.workSystem;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.types.GenerateContentResponse;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.modals.Modal;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import redis.RedisManager;
import shared.Cache;
import shared.Colors;
import shared.utils.CustomIdHelper;
import shared.utils.Utils;
import studio.styx.erisbot.core.ResponderInterface;
import studio.styx.erisbot.generated.enums.Contractstatus;
import studio.styx.erisbot.generated.enums.Rarity;
import studio.styx.erisbot.generated.tables.records.*;
import studio.styx.erisbot.generated.tables.references.TablesKt;
import studio.styx.erisbot.services.gemini.GeminiRequest;
import utils.ContainerRes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
public class WorkChallengeInteraction implements ResponderInterface {
    @Autowired
    private DSLContext dsl;

    private ContainerRes res = new ContainerRes();

    @Override
    public String getCustomId() {
        return "company/work/:userId";
    }

    @Override
    public void execute(ButtonInteractionEvent event) {
        CustomIdHelper customIdHelper = new CustomIdHelper(getCustomId(), event.getCustomId());

        String userId = customIdHelper.get("userId");

        if (!userId.equals(event.getUser().getId())) {
            res.setColor(Colors.DANGER).setText("Você não pode responder a essa pergunta!").send(event);
            return;
        }

        TextInput answer = TextInput.create("response", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Digite sua resposta aqui...")
                .setMinLength(10)
                .setMaxLength(1000)
                .build();

        Modal modal = Modal.create(
                        "company/work/" + userId,
                        "Responder à Pergunta"
                ).addComponents(Label.of("Sua Resposta", answer))
                .build();

        event.replyModal(modal).queue();
    }

    @Override
    public void execute(ModalInteractionEvent event) {
        String userId = event.getUser().getId();
        String situation = Cache.get(userId + "-situation");
        String response = event.getValue("response").getAsString();

        if (situation == null) {
            res.setColor(Colors.DANGER)
                    .setText("Você demorou demais pra responder ao desafio, por isso ele foi expirado")
                    .edit(event);
            return;
        }

        Cache.remove(userId + "-situation");

        event.deferEdit().queue(hook -> dsl.transaction(config -> {
            DSLContext tx = config.dsl();

            res.setColor(Colors.WARNING)
                    .setText("⏳ Aguarde enquanto a IA avalia sua resposta...")
                    .edit(hook);

            try {
                var USER = TablesKt.getUSER();
                var CONTRACT = TablesKt.getCONTRACT();
                var COMPANY = TablesKt.getCOMPANY();
                var USER_PET = TablesKt.getUSERPET();
                var PET_SKILLS = TablesKt.getUSERPETSKILL();
                var SKILLS = TablesKt.getPETSKILL();
                var PETS = TablesKt.getPET();

                // Buscar usuário com todas as relações
                var userResult = tx.select(
                                USER.asterisk(),
                                CONTRACT.asterisk(),
                                COMPANY.asterisk(),
                                USER_PET.asterisk(),
                                PET_SKILLS.asterisk(),
                                SKILLS.asterisk(),
                                PETS.getRARITY()
                        )
                        .from(USER)
                        .leftJoin(CONTRACT).on(USER.getCONTRACTID().eq(CONTRACT.getID()))
                        .leftJoin(COMPANY).on(CONTRACT.getCOMPANYID().eq(COMPANY.getID()))
                        .leftJoin(USER_PET).on(USER.getACTIVEPETID().eq(USER_PET.getID()))
                        .leftJoin(PET_SKILLS).on(USER_PET.getID().eq(PET_SKILLS.getUSERPETID()))
                        .leftJoin(SKILLS).on(PET_SKILLS.getSKILLID().eq(SKILLS.getID()))
                        .leftJoin(PETS).on(USER_PET.getPETID().eq(PETS.getID()))
                        .where(USER.getID().eq(event.getUser().getId()))
                        .fetch();

                if (userResult.isEmpty()) {
                    res.setColor(Colors.DANGER)
                            .setText("❌ Você não trabalha em nenhuma empresa! use o comando **/emprego procurar** para procurar por uma empresa!")
                            .send(hook);
                    return;
                }

                var firstRow = userResult.get(0);
                UserRecord user = firstRow.into(USER);
                ContractRecord contract = firstRow.into(CONTRACT);
                CompanyRecord company = firstRow.into(COMPANY);

                if (contract == null || contract.getId() == null) {
                    res.setColor(Colors.DANGER)
                            .setText("❌ Você não trabalha em nenhuma empresa! use o comando **/emprego procurar** para procurar por uma empresa!")
                            .send(hook);
                    return;
                }

                // Coletar skills do pet ativo
                UserpetskillRecord workXpBonus = null;
                UserpetskillRecord workWageBonus = null;
                Rarity petRarity = null;

                for (var row : userResult) {
                    PetskillRecord skill = row.into(SKILLS);
                    if (skill != null && skill.getName() != null) {
                        switch (skill.getName()) {
                            case "work_xp_bonus":
                                workXpBonus = row.into(PET_SKILLS);
                                break;
                            case "work_bonus":
                                workWageBonus = row.into(PET_SKILLS);
                                break;
                        }
                    }

                    // Obter raridade do pet
                    if (petRarity == null) {
                        Rarity rarityStr = row.get(PETS.getRARITY());
                        if (rarityStr != null) {
                            try {
                                petRarity = rarityStr;
                            } catch (IllegalArgumentException e) {
                                // Usar valor padrão se não conseguir converter
                                petRarity = Rarity.COMUM;
                            }
                        }
                    }
                }

                // Formatar expectativas
                String companyExpectationsFormatted = formatExpectations(company.getExpectations());

                // Criar prompt para o Gemini
                String prompt = Utils.brBuilder(
                        "Avalie a resposta de um funcionário a uma situação simulada de trabalho. Use as informações abaixo para contextualizar a avaliação:",
                        "",
                        "Nome da empresa: " + company.getName(),
                        "",
                        "Descrição da empresa: " + company.getDescription(),
                        "",
                        "Dificuldade do desafio: " + company.getDifficulty() + " (1 = muito fácil, 10 = muito difícil)",
                        "",
                        "Expectativas da empresa nos funcionários: " + companyExpectationsFormatted,
                        "",
                        "Situação simulada: " + situation,
                        "",
                        "Resposta do usuário: " + response,
                        "",
                        "Com base nesses dados, avalie a resposta do usuário e retorne apenas um objeto JSON com o seguinte formato:",
                        "",
                        "{",
                        "    \"bonus\": 0,",
                        "    \"reason\": \"Explique aqui o motivo da nota, destacando pontos positivos e negativos da resposta.\"",
                        "}",
                        "",
                        "Regras importantes:",
                        "",
                        "    bonus deve ser um número inteiro entre -5 e 5, sem decimais.",
                        "",
                        "    Use valores negativos para respostas ruins, positivos para boas e 0 se for neutra.",
                        "",
                        "    A razão deve ser clara, objetiva e útil para o usuário entender como melhorar.",
                        "",
                        "    Retorne apenas o JSON, sem comentários, sem explicações fora do objeto."
                );

                GeminiRequest gemini = new GeminiRequest();
                GenerateContentResponse result = gemini.request(prompt);

                if (result == null || result.text() == null) {
                    res.setColor(Colors.DANGER)
                            .setText("❌ Ocorreu um erro ao processar sua requisição!")
                            .send(hook);
                    return;
                }

                String text = result.text().trim();

                // Remove bloco de código se existir
                if (text.startsWith("```json")) {
                    text = text.substring(7);
                }
                if (text.startsWith("```")) {
                    text = text.substring(3);
                }
                if (text.endsWith("```")) {
                    text = text.substring(0, text.length() - 3);
                }
                text = text.trim();

                // Parse da resposta do Gemini
                ObjectMapper mapper = new ObjectMapper();
                JsonNode jsonResponse = mapper.readTree(text);

                int bonus = jsonResponse.get("bonus").asInt();
                String reason = jsonResponse.get("reason").asText();

                // Calcular salário base
                BigDecimal baseWage = contract.getSalary();
                BigDecimal wageMultiplier = BigDecimal.ONE.add(BigDecimal.valueOf(0.1 * bonus));
                BigDecimal payValue = baseWage.multiply(wageMultiplier);

                // Aplicar bônus do pet no salário
                if (workWageBonus != null && petRarity != null) {
                    double petMultiplier = calculateSkillBonus(petRarity, workWageBonus.getLevel());
                    payValue = payValue.add(baseWage.multiply(BigDecimal.valueOf(petMultiplier)));
                }

                // Calcular XP
                int xpGain;
                if (bonus < 0) {
                    xpGain = (int) (Math.random() * 11) * -1; // -0 a -10
                } else if (bonus == 0) {
                    xpGain = (int) (Math.random() * 11); // 0 a 10
                } else {
                    xpGain = (int) (Math.random() * 51) + 10; // 10 a 60
                }

                // Aplicar bônus do pet no XP
                if (workXpBonus != null && petRarity != null) {
                    double multiplier = calculateSkillBonus(petRarity, workXpBonus.getLevel());
                    xpGain = (int) (xpGain * multiplier);
                }

                // Atualizar usuário
                tx.update(USER)
                        .set(USER.getMONEY(), USER.getMONEY().add(payValue))
                        .set(USER.getXP(), USER.getXP().add(BigDecimal.valueOf(xpGain)))
                        .where(USER.getID().eq(event.getUser().getId()))
                        .execute();

                // Buscar dados atualizados para mostrar
                var updatedUser = tx.selectFrom(USER)
                        .where(USER.getID().eq(event.getUser().getId()))
                        .fetchOne();

                // Enviar resposta baseada no bônus
                String formattedPayValue = Utils.formatNumber(payValue.doubleValue());
                String formattedMoney = Utils.formatNumber(updatedUser.getMoney().doubleValue());

                if (bonus < 0) {
                    res.setColor(Colors.DANGER)
                            .setText("😢 Sua resposta foi insatisfatória, por isso recebeu menos! Valor recebido: **Ꞩ " + formattedPayValue + "**\n\n**Avaliação:** " + reason)
                            .send(hook);
                } else if (bonus > 0) {
                    res.setColor(Colors.SUCCESS)
                            .setText("🎉 Sua resposta foi satisfatória, por isso recebeu mais! Valor recebido: **Ꞩ " + formattedPayValue + "**\n\n**Avaliação:** " + reason)
                            .send(hook);
                } else {
                    res.setColor(Colors.PRIMARY)
                            .setText("✅ Sua resposta foi neutra, por isso recebeu o mesmo salário! Valor recebido: **Ꞩ " + formattedPayValue + "**\n\n**Avaliação:** " + reason)
                            .send(hook);
                }

                handlerUserResponse(
                        response,
                        situation,
                        contract.getId(),
                        hook,
                        tx,
                        company
                );

                LocalDateTime willEndIn = LocalDateTime.now(ZoneOffset.UTC).plusHours(2);

                var COOLDOWNS = TablesKt.getCOOLDOWN();

                tx.insertInto(COOLDOWNS)
                        .set(COOLDOWNS.getUSERID(), event.getUser().getId())
                        .set(COOLDOWNS.getNAME(), "work")
                        .set(COOLDOWNS.getTIMESTAMP(), LocalDateTime.now(ZoneOffset.UTC))
                        .set(COOLDOWNS.getWILLENDIN(), willEndIn)
                        .onConflict(COOLDOWNS.getUSERID(), COOLDOWNS.getNAME()) // Campos da chave única
                        .doUpdate()
                        .set(COOLDOWNS.getTIMESTAMP(), LocalDateTime.now(ZoneOffset.UTC))
                        .set(COOLDOWNS.getWILLENDIN(), willEndIn)
                        .execute();
            } catch (Exception e) {
                e.printStackTrace();
                res.setColor(Colors.DANGER)
                        .setText("❌ Ocorreu um erro ao processar sua resposta. Tente novamente.")
                        .send(hook);
            }
        }));
    }

    private void handlerUserResponse(
            String answer, String response,
            int contractId, InteractionHook hook, DSLContext tx,
            CompanyRecord company
    ) {
        var WORKCHALLENGER = TablesKt.getWORKCHALLENGES();
        tx.insertInto(WORKCHALLENGER)
                .set(WORKCHALLENGER.getRESPONSE(), response)
                .set(WORKCHALLENGER.getCHALLENGE(), answer)
                .set(WORKCHALLENGER.getCONTRACTID(), contractId)
                .set(WORKCHALLENGER.getCREATEDAT(), LocalDateTime.now())
                .execute();

        List<WorkchallengesRecord> workchallenges = tx.selectFrom(WORKCHALLENGER)
                .where(WORKCHALLENGER.getCONTRACTID().eq(contractId))
                .fetch();

        boolean alreadyHasAnalyzed = RedisManager.getBlocking(contractId + "-analyzed") != null;

        if (alreadyHasAnalyzed) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        // Desafios da ultima semana
        List<WorkchallengesRecord> lastChallenges = workchallenges.stream()
                .filter(c -> c.getCreatedat() != null && c.getCreatedat().isAfter(now.minusWeeks(1)))
                .collect(Collectors.toList());

        if (lastChallenges.size() > 2) {
            // Formatar expectativas da empresa
            String companyExpectationsFormatted = formatExpectations(company.getExpectations());

            // Construir histórico de desafios e respostas
            StringBuilder challengesHistory = new StringBuilder();
            for (int i = 0; i < lastChallenges.size(); i++) {
                WorkchallengesRecord challenge = lastChallenges.get(i);
                challengesHistory.append("Desafio ").append(i + 1).append(":\n");
                challengesHistory.append("Situação: ").append(challenge.getChallenge()).append("\n");
                challengesHistory.append("Resposta: ").append(challenge.getResponse()).append("\n\n");
            }

            String prompt = Utils.brBuilder(
                    "Você é um gerente de RH analisando o desempenho de um funcionário com base em seus desafios de trabalho recentes.",
                    "",
                    "**Contexto da Empresa:**",
                    "Nome: " + company.getName(),
                    "Descrição: " + company.getDescription(),
                    "Dificuldade esperada: " + company.getDifficulty() + "/10",
                    "Expectativas: " + companyExpectationsFormatted,
                    "",
                    "**Histórico de Desafios do Funcionário (última semana):**",
                    challengesHistory.toString(),
                    "",
                    "**Sua tarefa:**",
                    "Analise o padrão de respostas do funcionário e decida se alguma ação deve ser tomada.",
                    "Considere:",
                    "- Qualidade e consistência das respostas",
                    "- Alinhamento com os valores da empresa",
                    "- Progresso ou regressão no desempenho",
                    "- Potencial para crescimento ou risco para a empresa",
                    "",
                    "**Retorne APENAS um objeto JSON no formato exato:**",
                    "{",
                    "  \"action\": \"FIRED\" | \"INCREMENT_SALARY\" | \"DECREMENT_SALARY\" | null",
                    "  \"reason\": \"Texto explicativo detalhando a análise e justificativa da decisão\"",
                    "}",
                    "",
                    "**Critérios para cada ação:**",
                    "- `FIRED`: Respostas consistentemente ruins, falta de alinhamento com valores da empresa, risco para a organização",
                    "- `INCREMENT_SALARY`: Respostas excepcionais, demonstração de crescimento, superação de expectativas",
                    "- `DECREMENT_SALARY\": Desempenho medíocre mas recuperável, necessidade de motivação adicional",
                    "- `null`: Desempenho dentro do esperado, sem necessidade de ações drásticas",
                    "",
                    "**Regras importantes:**",
                    "- Seja justo e baseie sua decisão apenas nas evidências fornecidas",
                    "- A razão deve ser detalhada, profissional e construtiva, porém não deve ser muito grande.",
                    "- Retorne APENAS o JSON, sem comentários adicionais ou formatação markdown"
            );

            try {
                GeminiRequest gemini = new GeminiRequest();
                GenerateContentResponse result = gemini.request(prompt);

                if (result != null && result.text() != null) {
                    String text = result.text().trim();

                    // Limpar resposta
                    if (text.startsWith("```json")) {
                        text = text.substring(7);
                    }
                    if (text.startsWith("```")) {
                        text = text.substring(3);
                    }
                    if (text.endsWith("```")) {
                        text = text.substring(0, text.length() - 3);
                    }
                    text = text.trim();

                    // Parse da resposta
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode analysisResult = mapper.readTree(text);

                    String action = analysisResult.has("action") && !analysisResult.get("action").isNull()
                            ? analysisResult.get("action").asText()
                            : null;
                    String reason = analysisResult.get("reason").asText();

                    // Aplicar ação baseada na análise
                    if (action != null) {
                        var CONTRACT = TablesKt.getCONTRACT();

                        switch (action.toUpperCase()) {
                            case "FIRED":
                                // Demitir funcionário
                                tx.update(CONTRACT)
                                        .set(CONTRACT.getSTATUS(), Contractstatus.FIRED)
                                        .set(CONTRACT.getUPDATEDAT(), LocalDateTime.now())
                                        .where(CONTRACT.getID().eq(contractId))
                                        .execute();

                                // Limpar contractId do usuário
                                var USER = TablesKt.getUSER();
                                tx.update(USER)
                                        .set(USER.getCONTRACTID(), (Integer) null)
                                        .where(USER.getCONTRACTID().eq(contractId))
                                        .execute();

                                // Enviar mensagem de demissão
                                res.setColor(Colors.DANGER)
                                        .setText("💼 **Aviso Importante**\n\n" +
                                                "Com base na análise do seu desempenho nos últimos desafios, a empresa tomou a decisão de encerrar seu contrato.\n\n" +
                                                "**Motivo:** " + reason + "\n\n" +
                                                "Seu contrato foi finalizado.")
                                        .send(hook);
                                break;

                            case "INCREMENT_SALARY":
                                // Aumentar salário em 20%
                                BigDecimal currentSalary = tx.select(CONTRACT.getSALARY())
                                        .from(CONTRACT)
                                        .where(CONTRACT.getID().eq(contractId))
                                        .fetchOneInto(BigDecimal.class);

                                BigDecimal newSalary = currentSalary.multiply(BigDecimal.valueOf(1.2));

                                tx.update(CONTRACT)
                                        .set(CONTRACT.getSALARY(), newSalary)
                                        .set(CONTRACT.getUPDATEDAT(), LocalDateTime.now())
                                        .where(CONTRACT.getID().eq(contractId))
                                        .execute();

                                res.setColor(Colors.SUCCESS)
                                        .setText("🎉 **Parabéns! Aumento de Salário**\n\n" +
                                                "Com base no seu excelente desempenho nos últimos desafios, a empresa decidiu aumentar seu salário em 20%!\n\n" +
                                                "**Novo salário:** " + Utils.formatNumber(newSalary.doubleValue()) + "\n" +
                                                "**Motivo:** " + reason)
                                        .send(hook);
                                break;

                            case "DECREMENT_SALARY":
                                // Reduzir salário em 15%
                                BigDecimal currentSalary2 = tx.select(CONTRACT.getSALARY())
                                        .from(CONTRACT)
                                        .where(CONTRACT.getID().eq(contractId))
                                        .fetchOneInto(BigDecimal.class);

                                BigDecimal newSalary2 = currentSalary2.multiply(BigDecimal.valueOf(0.85));

                                tx.update(CONTRACT)
                                        .set(CONTRACT.getSALARY(), newSalary2)
                                        .set(CONTRACT.getUPDATEDAT(), LocalDateTime.now())
                                        .where(CONTRACT.getID().eq(contractId))
                                        .execute();

                                res.setColor(Colors.WARNING)
                                        .setText("⚠️ **Ajuste de Salário**\n\n" +
                                                "Com base na análise do seu desempenho, a empresa fez um ajuste no seu salário.\n\n" +
                                                "**Novo salário:** " + Utils.formatNumber(newSalary2.doubleValue()) + "\n" +
                                                "**Motivo:** " + reason + "\n\n" +
                                                "Esperamos ver melhorias no seu desempenho futuro.")
                                        .send(hook);
                                break;

                            default:
                                // Nenhuma ação - desempenho normal
                                res.setColor(Colors.PRIMARY)
                                        .setText("📊 **Análise de Desempenho**\n\n" +
                                                "Sua performance nos últimos desafios foi analisada pela equipe de RH.\n\n" +
                                                "**Feedback:** " + reason + "\n\n" +
                                                "Continue com o bom trabalho!")
                                        .send(hook);
                                break;
                        }

                        RedisManager.setBlocking(contractId + "-analyzed", "true", 3, TimeUnit.DAYS);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                // Em caso de erro na análise, apenas continue sem ações
                System.err.println("Erro na análise de desempenho: " + e.getMessage());
            }
        }
    }

    private String formatExpectations(Object expectations) {
        if (expectations == null) {
            return "A empresa não tem expectativas definidas.";
        }

        try {
            // Se for uma String JSON, converter para objeto
            if (expectations instanceof String jsonString) {
                ObjectMapper mapper = new ObjectMapper();

                // Tentar parsear como array de strings
                try {
                    String[] stringArray = mapper.readValue(jsonString, String[].class);
                    return formatStringArrayExpectations(stringArray);
                } catch (Exception e1) {
                    // Tentar parsear como array de objetos com skill e level
                    try {
                        Map<String, Object>[] objectArray = mapper.readValue(jsonString, Map[].class);
                        return formatObjectArrayExpectations(objectArray);
                    } catch (Exception e2) {
                        return "Não foi possível formatar as expectativas da empresa.";
                    }
                }
            }
            // Se já for um array
            else if (expectations.getClass().isArray()) {
                Object[] array = (Object[]) expectations;
                if (array.length > 0 && array[0] instanceof String) {
                    return formatStringArrayExpectations(Arrays.copyOf(array, array.length, String[].class));
                } else {
                    return formatObjectArrayExpectations(Arrays.copyOf(array, array.length, Map[].class));
                }
            }
            // Se for uma List
            else if (expectations instanceof List<?> list) {
                if (!list.isEmpty() && list.get(0) instanceof String) {
                    return formatStringArrayExpectations(list.toArray(new String[0]));
                } else {
                    return formatObjectArrayExpectations(list.toArray(new Map[0]));
                }
            }

            return "A empresa não tem expectativas definidas.";

        } catch (Exception e) {
            e.printStackTrace();
            return "Erro ao formatar expectativas: " + e.getMessage();
        }
    }

    private String formatStringArrayExpectations(String[] expectations) {
        if (expectations == null || expectations.length == 0) {
            return "A empresa não tem expectativas definidas.";
        }

        String joined = String.join(", ", expectations);
        // Substituir a última vírgula por " e "
        if (expectations.length > 1) {
            int lastComma = joined.lastIndexOf(", ");
            if (lastComma != -1) {
                joined = joined.substring(0, lastComma) + " e " + joined.substring(lastComma + 2);
            }
        }
        return joined;
    }

    @SuppressWarnings("unchecked")
    private String formatObjectArrayExpectations(Map<String, Object>[] expectations) {
        if (expectations == null || expectations.length == 0) {
            return "A empresa não tem expectativas definidas.";
        }

        List<String> formatted = new ArrayList<>();
        for (Map<String, Object> exp : expectations) {
            if (exp.containsKey("skill") && exp.containsKey("level")) {
                String skill = String.valueOf(exp.get("skill"));
                String level = String.valueOf(exp.get("level"));
                formatted.add("Habilidade: " + skill + ", Nível: " + level);
            } else {
                formatted.add("Não foi possível formatar essa expectativa");
            }
        }
        return String.join(", ", formatted);
    }

    private double calculateSkillBonus(Rarity rarity, int level) {
        Map<Rarity, Double> rarityMultipliers = Map.of(
                Rarity.COMUM, 0.05,
                Rarity.UNCOMUM, 0.1,
                Rarity.RARE, 0.15,
                Rarity.EPIC, 0.25,
                Rarity.LEGENDARY, 0.4
        );

        double rarityMultiplier = rarityMultipliers.getOrDefault(rarity, 0.0);
        double levelMultiplier = level * 0.02;
        return 1 + rarityMultiplier + levelMultiplier;
    }

}
