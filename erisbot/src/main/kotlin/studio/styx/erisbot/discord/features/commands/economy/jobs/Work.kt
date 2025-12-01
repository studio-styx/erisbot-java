package studio.styx.erisbot.discord.features.commands.economy.jobs

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.DiscordLocale
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import org.jooq.Configuration
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import shared.Cache.get
import shared.Cache.set
import shared.Colors
import shared.utils.DiscordTimeStyle
import shared.utils.Utils.brBuilder
import shared.utils.Utils.formatDiscordTime
import shared.utils.Utils.formatNumber
import shared.utils.Utils.getRandomListValue
import studio.styx.erisbot.core.interfaces.CommandInterface
import studio.styx.erisbot.generated.enums.Rarity
import studio.styx.erisbot.generated.tables.records.*
import studio.styx.erisbot.generated.tables.references.*
import services.gemini.GeminiRequest
import utils.ComponentBuilder.ContainerBuilder.Companion.create
import utils.ContainerRes
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

@Component
@Suppress("unused")
class Work : CommandInterface {
    @Autowired
    lateinit var dsl: DSLContext

    private val res = ContainerRes()

    private val cooldowns: MutableMap<String, LocalDateTime> = ConcurrentHashMap()

    override fun getSlashCommandData(): SlashCommandData {
        return Commands.slash("work", "⚒️ ✦ Work at your company")
            .setNameLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "trabalhar")
            .setDescriptionLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "⚒️ ✦ Trabalhe na sua empresa")
            .setNameLocalization(DiscordLocale.SPANISH, "trabajar")
            .setDescriptionLocalization(DiscordLocale.SPANISH, "⚒️ ✦ Trabaja en tu empresa")
            .setNameLocalization(DiscordLocale.SPANISH_LATAM, "trabajar")
            .setDescriptionLocalization(DiscordLocale.SPANISH_LATAM, "⚒️ ✦ Trabaja en tu empresa")
            .setNameLocalization(DiscordLocale.ENGLISH_US, "work")
            .setDescriptionLocalization(DiscordLocale.ENGLISH_US, "⚒️ ✦ Work at your company")
    }

    override suspend fun execute(event: SlashCommandInteractionEvent) {
        // Verificar cooldown do IA
        if (cooldowns.containsKey(event.user.id)) {
            res.setColor(Colors.DANGER)
                .setText("A ia está gerando uma resposta pra você. tente novamente mais tarde")
                .setEphemeral()
                .send(event)
            return
        }

        // Verificar se já está em uma situação
        val cachedSituation = get<String?>(event.user.id + "-situation")
        if (cachedSituation != null) {
            res.setColor(Colors.DANGER)
                .setText("Você está participando de um desafio, aguarde ele expirar ou termine ele pra poder usar esse comando novamente.")
                .setEphemeral()
                .send(event)
            return
        }

        val cooldownCacheKey = "cooldown:work:${event.user.id}"
        val userCooldownCache = get<Instant?>(cooldownCacheKey)

        if (userCooldownCache != null && userCooldownCache.isAfter(Instant.now())) {
            res.setColor(Colors.DANGER)
                .setEphemeral()
                .setText(
                    "Acalme-se! você está sendo muito rápido! por favor tente novamente ${
                        formatDiscordTime(
                            userCooldownCache.epochSecond,
                            DiscordTimeStyle.RELATIVE
                        )
                    }"
                )
                .send(event)
            return
        }

        set(cooldownCacheKey, Instant.now().plusSeconds(20), 20)

        event.deferReply().queue(Consumer { hook: InteractionHook ->
            dsl.transaction { config: Configuration ->
                val tx = config.dsl()

                // Buscar usuário com todas as relações
                val userResult = tx.select(
                    USER.asterisk(),
                    CONTRACT.asterisk(),
                    COMPANY.asterisk(),
                    USERPET.asterisk(),
                    USERPETSKILL.asterisk(),
                    PETSKILL.asterisk(),
                    PET.RARITY
                )
                    .from(USER)
                    .leftJoin(CONTRACT).on(USER.CONTRACTID.eq(CONTRACT.ID))
                    .leftJoin(COMPANY).on(CONTRACT.COMPANYID.eq(COMPANY.ID))
                    .leftJoin(USERPET).on(USER.ACTIVEPETID.eq(USERPET.ID))
                    .leftJoin(USERPETSKILL).on(USERPET.ID.eq(USERPETSKILL.USERPETID))
                    .leftJoin(PETSKILL).on(USERPETSKILL.SKILLID.eq(PETSKILL.ID))
                    .leftJoin(PET).on(USERPET.PETID.eq(PET.ID))
                    .where(USER.ID.eq(event.user.id))
                    .fetch()

                if (userResult.isEmpty()) {
                    res.setColor(Colors.DANGER)
                        .setText("Você não tem um emprego! use o comando **/jobs search** para encontrar um emprego!")
                        .send(hook)
                    return@transaction
                }

                val firstRow = userResult[0]
                firstRow.into(USER)
                val contract = firstRow.into(CONTRACT)
                val company = firstRow.into(COMPANY)

                // Verificar se tem contrato
                if (contract.id == null) {
                    res.setColor(Colors.DANGER)
                        .setText("Você não tem um emprego! use o comando **/jobs search** para encontrar um emprego!")
                        .send(hook)
                    return@transaction
                }

                // Verificar cooldown do trabalho
                val workCooldown = tx.selectFrom(COOLDOWN)
                    .where(
                        COOLDOWN.USERID.eq(event.user.id)
                            .and(COOLDOWN.NAME.eq("work"))
                    )
                    .fetchOne()

                val now = LocalDateTime.now()
                if (workCooldown != null && workCooldown.willendin!!.isAfter(now)) {
                    val futureInstant = workCooldown.willendin!!.atZone(ZoneId.of("UTC")).toInstant()
                    val correctEpochSeconds = futureInstant.epochSecond

                    res.setColor(Colors.WARNING)
                        .setText(
                            "⏰ Você já trabalhou hoje. Tente novamente ${
                                formatDiscordTime(
                                    correctEpochSeconds,
                                    DiscordTimeStyle.RELATIVE
                                )
                            }"
                        )
                        .send(hook)
                    return@transaction
                }

                // Coletar skills do pet ativo
                var hasWorkChallengeEasier = false
                var workXpBonus: UserpetskillRecord? = null
                var workWageBonus: UserpetskillRecord? = null
                var petRarity: Rarity? = null

                for (row in userResult) {
                    val skill = row.into(PETSKILL)
                    if (skill.name != null) {
                        when (skill.name) {
                            "work_challenge_easier" -> hasWorkChallengeEasier = true
                            "work_xp_bonus" -> workXpBonus = row.into(USERPETSKILL)
                            "work_bonus" -> workWageBonus = row.into(USERPETSKILL)
                        }
                    }

                    // Obter raridade do pet
                    if (petRarity == null) {
                        val rarityStr = row.get(PET.RARITY)
                        if (rarityStr != null) {
                            petRarity = rarityStr
                        }
                    }
                }

                // Calcular probabilidade de situação
                var percentage = 40
                val flags = company.flags
                if (flags != null) {
                    val has100Situation = flags.any { it == "100%_SITUATION" }
                    val hasNoSituation = flags.any { it == "NO_SITUATION" }

                    if (has100Situation) {
                        percentage = 100
                    } else if (hasNoSituation) {
                        percentage = 0
                    }
                }

                if (calculateProbability(percentage)) {
                    // Situação ocorreu - gerar desafio
                    cooldowns[event.user.id] = LocalDateTime.now().plusMinutes(4)

                    res.setColor(Colors.WARNING)
                        .setText("⏳ Um novo desafio apareceu! por favor aguarde um instante.")
                        .edit(hook)

                    // Gerar prompt para o Gemini
                    val prompt = getWorkChallengePrompt(
                        event.user.name,
                        company,
                        formatExpectations(company.expectations),
                        hasWorkChallengeEasier
                    )

                    // Chamar Gemini
                    val gemini = GeminiRequest()
                    val geminiResult = gemini.request(prompt)

                    if (geminiResult?.text() == null) {
                        // Erro no Gemini - pagar salário normal
                        val wage = contract.salary
                        tx.update(USER)
                            .set(USER.MONEY, USER.MONEY.add(wage))
                            .where(USER.ID.eq(event.user.id))
                            .execute()

                        res.setColor(Colors.DANGER)
                            .setText(
                                "😢 Ocorreu um erro ao gerar o desafio, por isso você recebeu o salário normal de: ${
                                    formatNumber(
                                        wage!!.toDouble()
                                    )
                                }"
                            )
                            .send(hook)
                        return@transaction
                    }

                    val situation = geminiResult.text()
                    set(event.user.id + "-situation", situation!!)

                    // Criar container com a situação
                    val container = create()
                        .withColor(Colors.WARNING)
                        .addText("## Um novo desafio surgiu! 🎯")
                        .addText("Responda a pergunta abaixo, como você reagiria a essa situação?\n-# ╰ obs: se você responder corretamente pode até ganhar um aumento hoje!")
                        .addDivider(false)
                        .addText(situation)
                        .addRow(
                            ActionRow.of(
                                Button.primary("company/work/${event.user.id}", "Responder")
                            )
                        )
                        .build()

                    hook.editOriginalComponents(container).useComponentsV2().queue()
                } else {
                    // Trabalho normal - calcular bônus e pagar
                    val baseWage = company.wage
                    var finalWage = baseWage
                    val baseXp = 10 + (Math.random() * 16).toInt() // 10-25 XP
                    var finalXp = baseXp

                    // Aplicar bônus de salário
                    if (workWageBonus != null && petRarity != null) {
                        val multiplier = calculateSkillBonus(petRarity, workWageBonus.level!!)
                        finalWage = baseWage!!.multiply(BigDecimal.valueOf(multiplier))
                    }

                    // Aplicar bônus de XP
                    if (workXpBonus != null && petRarity != null) {
                        val multiplier = calculateSkillBonus(petRarity, workXpBonus.level!!)
                        finalXp = (baseXp * multiplier).toInt()
                    }

                    // Atualizar usuário
                    tx.update(USER)
                        .set(USER.MONEY, USER.MONEY.add(finalWage))
                        .set(USER.XP, USER.XP.add(BigDecimal.valueOf(finalXp.toLong())))
                        .where(USER.ID.eq(event.user.id))
                        .execute()

                    // Buscar dados atualizados
                    val updatedUser = tx.selectFrom(USER)
                        .where(USER.ID.eq(event.user.id))
                        .fetchOne()

                    val successContainer = create()
                        .withColor(Colors.SUCCESS)
                        .addText("## Você trabalhou e recebeu seu salário de: **${formatNumber(finalWage!!.toDouble())}** stx 💰")
                        .addText("> Você agora possui: **${formatNumber(updatedUser!!.money!!.toDouble())}** styx em sua carteira!")
                        .addText("> E possui: **${updatedUser.xp}** xp!")
                        .build()

                    hook.editOriginalComponents(successContainer).useComponentsV2().queue()
                }

                // Atualizar cooldown
                val willEndIn = LocalDateTime.now(ZoneOffset.UTC).plusHours(2)
                tx.insertInto(COOLDOWN)
                    .set(COOLDOWN.USERID, event.user.id)
                    .set(COOLDOWN.NAME, "work")
                    .set(COOLDOWN.TIMESTAMP, LocalDateTime.now(ZoneOffset.UTC))
                    .set(COOLDOWN.WILLENDIN, willEndIn)
                    .onConflict(COOLDOWN.USERID, COOLDOWN.NAME) // Campos da chave única
                    .doUpdate()
                    .set(COOLDOWN.TIMESTAMP, LocalDateTime.now(ZoneOffset.UTC))
                    .set(COOLDOWN.WILLENDIN, willEndIn)
                    .execute()
            }
        })
    }

    private fun calculateSkillBonus(rarity: Rarity?, level: Int): Double {
        val rarityMultipliers = mapOf(
            Rarity.COMUM to 0.05,
            Rarity.UNCOMUM to 0.1,
            Rarity.RARE to 0.15,
            Rarity.EPIC to 0.25,
            Rarity.LEGENDARY to 0.4
        )

        val rarityMultiplier = rarityMultipliers.getOrDefault(rarity, 0.0)
        val levelMultiplier = level * 0.02
        return 1 + rarityMultiplier + levelMultiplier
    }

    private fun calculateProbability(percentage: Int): Boolean {
        return (Math.random() * 100) < percentage
    }

    private fun getWorkChallengePrompt(
        userName: String,
        company: CompanyRecord,
        expectations: String,
        hasEasierSkill: Boolean
    ): String {
        // Por enquanto vamos usar apenas português, você pode adicionar suporte a outras línguas depois
        val langPrompt = "Responda em português:"

        val basePrompts = listOf(
            brBuilder(
                langPrompt,
                "O usuário $userName está trabalhando em sua empresa.",
                "Crie um desafio realista com base nas seguintes informações:",
                "",
                "Nome da empresa: ${company.name}",
                "Descrição: ${company.description}",
                "Dificuldade: ${company.difficulty} (1 = muito fácil, 10 = muito difícil)",
                "Expectativas nos funcionários: $expectations",
                "",
                "Gere uma simulação de situação que poderia ocorrer no dia a dia de trabalho, de acordo com o nível de dificuldade. A situação deve exigir que o usuário diga como reagiria.",
                "Não é uma pergunta de entrevista.",
                "",
                "Retorne apenas a pergunta, sem explicações, sem aspas e sem comentários adicionais."
            ),

            brBuilder(
                "Você é $userName, funcionário da empresa ${company.name}.",
                "Sua empresa é descrita assim: ${company.description}",
                "Ela espera de seus funcionários: $expectations",
                "",
                "Crie uma situação inesperada ou desafiadora que possa acontecer nesse ambiente.",
                "Use a dificuldade (${company.difficulty}) para ajustar o nível de pressão ou complexidade.",
                "",
                "Descreva a situação como se estivesse acontecendo agora e peça que o usuário diga como reagiria.",
                "",
                "Apenas a pergunta, sem explicações, aspas ou comentários."
            ),

            brBuilder(
                "Simule um evento de trabalho para $userName, empregado da empresa ${company.name}.",
                "Detalhes: ${company.description}",
                "Expectativas: $expectations",
                "Dificuldade: ${company.difficulty}",
                "",
                "Crie um desafio típico do ambiente profissional, adequado à dificuldade.",
                "A situação deve exigir uma decisão prática, não ser uma pergunta de entrevista.",
                "",
                "Retorne somente a pergunta, de forma direta."
            )
        )

        val easierPrompts = listOf(
            brBuilder(
                "O usuário $userName está trabalhando na empresa ${company.name}.",
                "Seu pet reduziu a complexidade do desafio de hoje 🐾",
                "",
                "Crie uma situação mais simples, cotidiana, relacionada ao ambiente descrito:",
                "Descrição: ${company.description}",
                "Expectativas: $expectations",
                "",
                "A dificuldade deve ser reduzida (ex.: um pequeno imprevisto ou tarefa inesperada, não um problema complexo).",
                "",
                "Peça que o usuário diga como reagiria, sem explicações adicionais."
            ),

            brBuilder(
                "Simule um pequeno desafio no dia de trabalho de $userName na empresa ${company.name}.",
                "O pet do usuário está ajudando a tornar as coisas mais fáceis hoje 🐾",
                "",
                "Crie uma situação leve, mas ainda plausível para um ambiente profissional com essas características:",
                "Descrição: ${company.description}",
                "Expectativas: $expectations",
                "",
                "A dificuldade deve ser visivelmente menor que ${company.difficulty}, com foco em tarefas rotineiras ou problemas simples.",
                "",
                "Apenas a pergunta final, direta e clara."
            )
        )

        val pool = if (hasEasierSkill) easierPrompts else basePrompts
        return getRandomListValue(pool)
    }

    private fun formatExpectations(expectations: Any?): String {
        if (expectations == null) {
            return "A empresa não tem expectativas definidas."
        }

        try {
            // Se for uma String JSON, converter para objeto
            if (expectations is String) {
                val mapper = ObjectMapper()

                // Tentar parsear como array de strings
                try {
                    val stringArray = mapper.readValue(expectations, Array<String>::class.java)
                    return formatStringArrayExpectations(stringArray)
                } catch (_: Exception) {
                    // Tentar parsear como array de objetos com skill e level
                    try {
                        val objectArray = mapper.readValue(
                            expectations,
                            object : TypeReference<Array<Map<String, Any>>>() {})
                        return formatObjectArrayExpectations(objectArray)
                    } catch (_: Exception) {
                        return "Não foi possível formatar as expectativas da empresa."
                    }
                }
            } else if (expectations.javaClass.isArray) {
                @Suppress("UNCHECKED_CAST")
                val array = expectations as Array<Any>
                if (array.isNotEmpty() && array[0] is String) {
                    @Suppress("UNCHECKED_CAST")
                    return formatStringArrayExpectations(array as Array<String>)
                } else {
                    @Suppress("UNCHECKED_CAST")
                    return formatObjectArrayExpectations(array as Array<Map<String, Any>>)
                }
            } else if (expectations is List<*>) {
                if (expectations.isNotEmpty() && expectations[0] is String) {
                    @Suppress("UNCHECKED_CAST")
                    return formatStringArrayExpectations((expectations as List<String>).toTypedArray())
                } else {
                    @Suppress("UNCHECKED_CAST")
                    return formatObjectArrayExpectations((expectations as List<Map<String, Any>>).toTypedArray())
                }
            }

            return "A empresa não tem expectativas definidas."
        } catch (e: Exception) {
            e.printStackTrace()
            return "Erro ao formatar expectativas: ${e.message}"
        }
    }

    private fun formatStringArrayExpectations(expectations: Array<String>?): String {
        if (expectations.isNullOrEmpty()) {
            return "A empresa não tem expectativas definidas."
        }

        var joined = expectations.joinToString(", ")
        // Substituir a última vírgula por " e "
        if (expectations.size > 1) {
            val lastComma = joined.lastIndexOf(", ")
            if (lastComma != -1) {
                joined = joined.take(lastComma) + " e " + joined.substring(lastComma + 2)
            }
        }
        return joined
    }

    private fun formatObjectArrayExpectations(expectations: Array<Map<String, Any>>?): String {
        if (expectations.isNullOrEmpty()) {
            return "A empresa não tem expectativas definidas."
        }

        val formatted = mutableListOf<String>()
        for (exp in expectations) {
            if (exp.containsKey("skill") && exp.containsKey("level")) {
                val skill = exp["skill"].toString()
                val level = exp["level"].toString()
                formatted.add("Habilidade: $skill, Nível: $level")
            } else {
                formatted.add("Não foi possível formatar essa expectativa")
            }
        }
        return formatted.joinToString(", ")
    }
}
