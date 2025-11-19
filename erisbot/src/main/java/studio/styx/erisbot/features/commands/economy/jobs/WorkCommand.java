package studio.styx.erisbot.features.commands.economy.jobs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.types.GenerateContentResponse;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import shared.Cache;
import shared.Colors;
import shared.utils.DiscordTimeStyle;
import shared.utils.Utils;
import studio.styx.erisbot.core.CommandInterface;
import studio.styx.erisbot.generated.enums.Rarity;
import studio.styx.erisbot.generated.tables.records.*;
import studio.styx.erisbot.generated.tables.references.TablesKt;
import studio.styx.erisbot.services.gemini.GeminiRequest;
import utils.ComponentBuilder;
import utils.ContainerRes;

import java.math.BigDecimal;
import java.time.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WorkCommand implements CommandInterface {
    @Autowired
    private DSLContext dsl;

    private ContainerRes res = new ContainerRes();

    private final Map<String, LocalDateTime> cooldowns = new ConcurrentHashMap<>();

    @Override
    public SlashCommandData getSlashCommandData() {
        return Commands.slash("work", "⚒️ ✦ Work at your company")
                .setNameLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "trabalhar")
                .setDescriptionLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "⚒️ ✦ Trabalhe na sua empresa")
                .setNameLocalization(DiscordLocale.SPANISH, "trabajar")
                .setDescriptionLocalization(DiscordLocale.SPANISH, "⚒️ ✦ Trabaja en tu empresa")
                .setNameLocalization(DiscordLocale.SPANISH_LATAM, "trabajar")
                .setDescriptionLocalization(DiscordLocale.SPANISH_LATAM, "⚒️ ✦ Trabaja en tu empresa")
                .setNameLocalization(DiscordLocale.ENGLISH_US, "work")
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US, "⚒️ ✦ Work at your company");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        // Verificar cooldown do IA
        if (cooldowns.containsKey(event.getUser().getId())) {
            res.setColor(Colors.DANGER)
                    .setText("A ia está gerando uma resposta pra você. tente novamente mais tarde")
                    .setEphemeral()
                    .send(event);
            return;
        }

        // Verificar se já está em uma situação
        String cachedSituation = Cache.get(event.getUser().getId() + "-situation");
        if (cachedSituation != null) {
            res.setColor(Colors.DANGER)
                    .setText("Você está participando de um desafio, aguarde ele expirar ou termine ele pra poder usar esse comando novamente.")
                    .setEphemeral()
                    .send(event);
            return;
        }

        String cooldownCacheKey = "cooldown:work:" + event.getUser().getId();
        Instant userCooldownCache = Cache.get(cooldownCacheKey);

        if (userCooldownCache != null && userCooldownCache.isAfter(Instant.now())) {
            res.setColor(Colors.DANGER)
                    .setEphemeral()
                    .setText("Acalme-se! você está sendo muito rápido! por favor tente novamente " +
                            Utils.formatDiscordTime(userCooldownCache.getEpochSecond(), DiscordTimeStyle.RELATIVE))
                    .send(event);
            return;
        }

        Cache.set(cooldownCacheKey, Instant.now().plusSeconds(20), 20);

        event.deferReply().queue(hook -> dsl.transaction(config -> {
            DSLContext tx = config.dsl();

            var USER = TablesKt.getUSER();
            var CONTRACT = TablesKt.getCONTRACT();
            var COMPANY = TablesKt.getCOMPANY();
            var COOLDOWNS = TablesKt.getCOOLDOWN();
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
                        .setText("Você não tem um emprego! use o comando **/jobs search** para encontrar um emprego!")
                        .send(hook);
                return;
            }

            var firstRow = userResult.get(0);
            UserRecord user = firstRow.into(USER);
            ContractRecord contract = firstRow.into(CONTRACT);
            CompanyRecord company = firstRow.into(COMPANY);

            // Verificar se tem contrato
            if (contract == null || contract.getId() == null) {
                res.setColor(Colors.DANGER)
                        .setText("Você não tem um emprego! use o comando **/jobs search** para encontrar um emprego!")
                        .send(hook);
                return;
            }

            // Verificar cooldown do trabalho
            var workCooldown = tx.selectFrom(COOLDOWNS)
                    .where(COOLDOWNS.getUSERID().eq(event.getUser().getId())
                            .and(COOLDOWNS.getNAME().eq("work")))
                    .fetchOne();

            LocalDateTime now = LocalDateTime.now();
            if (workCooldown != null && workCooldown.getWillendin().isAfter(now)) {
                Instant futureInstant = workCooldown.getWillendin().atZone(ZoneId.of("UTC")).toInstant();
                long correctEpochSeconds = futureInstant.getEpochSecond();

                res.setColor(Colors.WARNING)
                        .setText("⏰ Você já trabalhou hoje. Tente novamente " + Utils.formatDiscordTime(correctEpochSeconds, DiscordTimeStyle.RELATIVE))
                        .send(hook);
                return;
            }

            // Coletar skills do pet ativo
            boolean hasWorkChallengeAvoid = false;
            boolean hasWorkChallengeEasier = false;
            UserpetskillRecord workXpBonus = null;
            UserpetskillRecord workWageBonus = null;
            Rarity petRarity = null;

            for (var row : userResult) {
                PetskillRecord skill = row.into(SKILLS);
                if (skill != null && skill.getName() != null) {
                    switch (skill.getName()) {
                        case "work_challenge_avoid":
                            hasWorkChallengeAvoid = true;
                            break;
                        case "work_challenge_easier":
                            hasWorkChallengeEasier = true;
                            break;
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
                        petRarity = rarityStr;
                    }
                }
            }

            // Calcular probabilidade de situação
            int percentage = 40;
            String[] flags = company.getFlags();
            if (flags != null) {
                boolean has100Situation = Arrays.stream(flags)
                        .anyMatch(flag -> flag != null && flag.equals("100%_SITUATION"));
                boolean hasNoSituation = Arrays.stream(flags)
                        .anyMatch(flag -> flag != null && flag.equals("NO_SITUATION"));

                if (has100Situation) {
                    percentage = 100;
                } else if (hasNoSituation) {
                    percentage = 0;
                }
            }

            if (calculateProbability(percentage)) {
                // Situação ocorreu - gerar desafio
                cooldowns.put(event.getUser().getId(), LocalDateTime.now().plusMinutes(4));

                res.setColor(Colors.WARNING)
                        .setText("⏳ Um novo desafio apareceu! por favor aguarde um instante.")
                        .edit(hook);

                // Gerar prompt para o Gemini
                String prompt = getWorkChallengePrompt(
                        event.getUser().getName(),
                        company,
                        formatExpectations(company.getExpectations()),
                        hasWorkChallengeEasier
                );

                // Chamar Gemini
                GeminiRequest gemini = new GeminiRequest();
                GenerateContentResponse geminiResult = gemini.request(prompt);

                if (geminiResult == null || geminiResult.text() == null) {
                    // Erro no Gemini - pagar salário normal
                    BigDecimal wage = contract.getSalary();
                    tx.update(USER)
                            .set(USER.getMONEY(), USER.getMONEY().add(wage))
                            .where(USER.getID().eq(event.getUser().getId()))
                            .execute();

                    res.setColor(Colors.DANGER)
                            .setText("😢 Ocorreu um erro ao gerar o desafio, por isso você recebeu o salário normal de: " + Utils.formatNumber(wage.doubleValue()))
                            .send(hook);
                    return;
                }

                String situation = geminiResult.text();
                Cache.set(event.getUser().getId() + "-situation", situation);

                // Criar container com a situação
                var container = ComponentBuilder.ContainerBuilder.create()
                        .withColor(Colors.WARNING)
                        .addText("## Um novo desafio surgiu! 🎯")
                        .addText("Responda a pergunta abaixo, como você reagiria a essa situação?\n-# ╰ obs: se você responder corretamente pode até ganhar um aumento hoje!")
                        .addDivider(false)
                        .addText(situation)
                        .addRow(ActionRow.of(
                                Button.primary("company/work/" + event.getUser().getId(), "Responder")
                        ))
                        .build();

                hook.editOriginalComponents(container).useComponentsV2().queue();

            } else {
                // Trabalho normal - calcular bônus e pagar
                BigDecimal baseWage = company.getWage();
                BigDecimal finalWage = baseWage;
                int baseXp = 10 + (int)(Math.random() * 16); // 10-25 XP
                int finalXp = baseXp;

                // Aplicar bônus de salário
                if (workWageBonus != null && petRarity != null) {
                    double multiplier = calculateSkillBonus(petRarity, workWageBonus.getLevel());
                    finalWage = baseWage.multiply(BigDecimal.valueOf(multiplier));
                }

                // Aplicar bônus de XP
                if (workXpBonus != null && petRarity != null) {
                    double multiplier = calculateSkillBonus(petRarity, workXpBonus.getLevel());
                    finalXp = (int)(baseXp * multiplier);
                }

                // Atualizar usuário
                tx.update(USER)
                        .set(USER.getMONEY(), USER.getMONEY().add(finalWage))
                        .set(USER.getXP(), USER.getXP().add(BigDecimal.valueOf(finalXp)))
                        .where(USER.getID().eq(event.getUser().getId()))
                        .execute();

                // Buscar dados atualizados
                var updatedUser = tx.selectFrom(USER)
                        .where(USER.getID().eq(event.getUser().getId()))
                        .fetchOne();

                var successContainer = ComponentBuilder.ContainerBuilder.create()
                        .withColor(Colors.SUCCESS)
                        .addText("## Você trabalhou e recebeu seu salário de: **" + Utils.formatNumber(finalWage.doubleValue()) + "** stx 💰")
                        .addText("> Você agora possui: **" + Utils.formatNumber(updatedUser.getMoney().doubleValue()) + "** styx em sua carteira!")
                        .addText("> E possui: **" + updatedUser.getXp() + "** xp!")
                        .build();

                hook.editOriginalComponents(successContainer).useComponentsV2().queue();
            }

            // Atualizar cooldown
            LocalDateTime willEndIn = LocalDateTime.now(ZoneOffset.UTC).plusHours(2);

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
        }));
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

    private boolean calculateProbability(int percentage) {
        return (Math.random() * 100) < percentage;
    }

    private String getWorkChallengePrompt(String userName, CompanyRecord company, String expectations, boolean hasEasierSkill) {
        // Por enquanto vamos usar apenas português, você pode adicionar suporte a outras línguas depois
        String langPrompt = "Responda em português:";

        List<String> basePrompts = List.of(
                Utils.brBuilder(
                        langPrompt,
                        "O usuário " + userName + " está trabalhando em sua empresa.",
                        "Crie um desafio realista com base nas seguintes informações:",
                        "",
                        "Nome da empresa: " + company.getName(),
                        "Descrição: " + company.getDescription(),
                        "Dificuldade: " + company.getDifficulty() + " (1 = muito fácil, 10 = muito difícil)",
                        "Expectativas nos funcionários: " + expectations,
                        "",
                        "Gere uma simulação de situação que poderia ocorrer no dia a dia de trabalho, de acordo com o nível de dificuldade. A situação deve exigir que o usuário diga como reagiria.",
                        "Não é uma pergunta de entrevista.",
                        "",
                        "Retorne apenas a pergunta, sem explicações, sem aspas e sem comentários adicionais."
                ),

                Utils.brBuilder(
                        "Você é " + userName + ", funcionário da empresa " + company.getName() + ".",
                        "Sua empresa é descrita assim: " + company.getDescription(),
                        "Ela espera de seus funcionários: " + expectations,
                        "",
                        "Crie uma situação inesperada ou desafiadora que possa acontecer nesse ambiente.",
                        "Use a dificuldade (" + company.getDifficulty() + ") para ajustar o nível de pressão ou complexidade.",
                        "",
                        "Descreva a situação como se estivesse acontecendo agora e peça que o usuário diga como reagiria.",
                        "",
                        "Apenas a pergunta, sem explicações, aspas ou comentários."
                ),

                Utils.brBuilder(
                        "Simule um evento de trabalho para " + userName + ", empregado da empresa " + company.getName() + ".",
                        "Detalhes: " + company.getDescription(),
                        "Expectativas: " + expectations,
                        "Dificuldade: " + company.getDifficulty(),
                        "",
                        "Crie um desafio típico do ambiente profissional, adequado à dificuldade.",
                        "A situação deve exigir uma decisão prática, não ser uma pergunta de entrevista.",
                        "",
                        "Retorne somente a pergunta, de forma direta."
                )
        );

        List<String> easierPrompts = List.of(
                Utils.brBuilder(
                        "O usuário " + userName + " está trabalhando na empresa " + company.getName() + ".",
                        "Seu pet reduziu a complexidade do desafio de hoje 🐾",
                        "",
                        "Crie uma situação mais simples, cotidiana, relacionada ao ambiente descrito:",
                        "Descrição: " + company.getDescription(),
                        "Expectativas: " + expectations,
                        "",
                        "A dificuldade deve ser reduzida (ex.: um pequeno imprevisto ou tarefa inesperada, não um problema complexo).",
                        "",
                        "Peça que o usuário diga como reagiria, sem explicações adicionais."
                ),

                Utils.brBuilder(
                        "Simule um pequeno desafio no dia de trabalho de " + userName + " na empresa " + company.getName() + ".",
                        "O pet do usuário está ajudando a tornar as coisas mais fáceis hoje 🐾",
                        "",
                        "Crie uma situação leve, mas ainda plausível para um ambiente profissional com essas características:",
                        "Descrição: " + company.getDescription(),
                        "Expectativas: " + expectations,
                        "",
                        "A dificuldade deve ser visivelmente menor que " + company.getDifficulty() + ", com foco em tarefas rotineiras ou problemas simples.",
                        "",
                        "Apenas a pergunta final, direta e clara."
                )
        );

        List<String> pool = hasEasierSkill ? easierPrompts : basePrompts;
        return Utils.getRandomListValue(pool);
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
}
