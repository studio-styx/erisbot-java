package studio.styx.erisbot.discord.features.interactions.economy.WorkSystem

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import dev.minn.jda.ktx.interactions.components.TextInput
import net.dv8tion.jda.api.components.label.Label
import net.dv8tion.jda.api.components.textinput.TextInput
import net.dv8tion.jda.api.components.textinput.TextInputStyle
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.modals.Modal
import org.jooq.Configuration
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import redis.RedisManager.getBlocking
import redis.RedisManager.setBlocking
import services.gemini.GeminiRequest
import shared.Cache.get
import shared.Cache.remove
import shared.Colors
import shared.utils.CustomIdHelper
import shared.utils.Utils.brBuilder
import shared.utils.Utils.formatNumber
import studio.styx.erisbot.core.interfaces.ResponderInterface
import studio.styx.erisbot.generated.enums.Contractstatus
import studio.styx.erisbot.generated.enums.Rarity
import studio.styx.erisbot.generated.tables.records.CompanyRecord
import studio.styx.erisbot.generated.tables.records.UserpetskillRecord
import studio.styx.erisbot.generated.tables.references.*
import utils.ContainerRes
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

@Component
@Suppress("unused")
class WorkChallengeInteraction : ResponderInterface {
    @Autowired
    lateinit var dsl: DSLContext

    private val res = ContainerRes()

    override val customId = "company/work/:userId"

    override suspend fun execute(event: ButtonInteractionEvent) {
        val customIdHelper = CustomIdHelper(customId, event.customId)

        val userId = customIdHelper.get("userId")

        if (userId != event.user.id) {
            res.setColor(Colors.DANGER).setText("Você não pode responder a essa pergunta!").send(event)
            return
        }

        val answer = TextInput.create("response", TextInputStyle.PARAGRAPH)
            .setPlaceholder("Digite sua resposta aqui...")
            .setMinLength(10)
            .setMaxLength(1000)
            .build()

        val modal = Modal.create(
            "company/work/$userId",
            "Responder à Pergunta"
        ).addComponents(Label.of("Sua Resposta", answer))
            .build()

        event.replyModal(modal).queue()
    }

    override suspend fun execute(event: ModalInteractionEvent) {
        val userId = event.user.id
        val situation = get<String?>("$userId-situation")
        val response = event.getValue("response")!!.asString

        if (situation == null) {
            res.setColor(Colors.DANGER)
                .setText("Você demorou demais pra responder ao desafio, por isso ele foi expirado")
                .edit(event)
            return
        }

        remove("$userId-situation")

        event.deferEdit().queue { hook: InteractionHook ->
            dsl.transaction { config: Configuration ->
                val tx = config.dsl()
                res.setColor(Colors.WARNING)
                    .setText("⏳ Aguarde enquanto a IA avalia sua resposta...")
                    .edit(hook)
                try {
                    val userTable = USER
                    val contractTable = CONTRACT
                    val companyTable = COMPANY
                    val userPetTable = USERPET
                    val petSkillsTable = USERPETSKILL
                    val skillsTable = PETSKILL
                    val petsTable = PET

                    // Buscar usuário com todas as relações
                    val userResult = tx.select(
                        userTable.asterisk(),
                        contractTable.asterisk(),
                        companyTable.asterisk(),
                        userPetTable.asterisk(),
                        petSkillsTable.asterisk(),
                        skillsTable.asterisk(),
                        petsTable.RARITY
                    )
                        .from(userTable)
                        .leftJoin(contractTable).on(userTable.CONTRACTID.eq(contractTable.ID))
                        .leftJoin(companyTable).on(contractTable.COMPANYID.eq(companyTable.ID))
                        .leftJoin(userPetTable).on(userTable.ACTIVEPETID.eq(userPetTable.ID))
                        .leftJoin(petSkillsTable).on(userPetTable.ID.eq(petSkillsTable.USERPETID))
                        .leftJoin(skillsTable).on(petSkillsTable.SKILLID.eq(skillsTable.ID))
                        .leftJoin(petsTable).on(userPetTable.PETID.eq(petsTable.ID))
                        .where(userTable.ID.eq(event.user.id))
                        .fetch()

                    if (userResult.isEmpty()) {
                        res.setColor(Colors.DANGER)
                            .setText("❌ Você não trabalha em nenhuma empresa! use o comando **/emprego procurar** para procurar por uma empresa!")
                            .send(hook)
                        return@transaction
                    }

                    val firstRow = userResult[0]
                    firstRow.into(userTable)
                    val contract = firstRow.into(contractTable)
                    val company = firstRow.into(companyTable)

                    if (contract.id == null) {
                        res.setColor(Colors.DANGER)
                            .setText("❌ Você não trabalha em nenhuma empresa! use o comando **/emprego procurar** para procurar por uma empresa!")
                            .send(hook)
                        return@transaction
                    }

                    // Coletar skills do pet ativo
                    var workXpBonus: UserpetskillRecord? = null
                    var workWageBonus: UserpetskillRecord? = null
                    var petRarity: Rarity? = null

                    for (row in userResult) {
                        val skill = row.into(skillsTable)
                        if (skill.name != null) {
                            when (skill.name) {
                                "work_xp_bonus" -> workXpBonus = row.into(petSkillsTable)
                                "work_bonus" -> workWageBonus = row.into(petSkillsTable)
                            }
                        }

                        // Obter raridade do pet
                        if (petRarity == null) {
                            val rarityStr = row.get(petsTable.RARITY)
                            if (rarityStr != null) {
                                petRarity = try {
                                    rarityStr
                                } catch (_: IllegalArgumentException) {
                                    // Usar valor padrão se não conseguir converter
                                    Rarity.COMUM
                                }
                            }
                        }
                    }

                    // Formatar expectativas
                    val companyExpectationsFormatted = formatExpectations(company.expectations)

                    // Criar prompt para o Gemini
                    val prompt = brBuilder(
                        "Avalie a resposta de um funcionário a uma situação simulada de trabalho. Use as informações abaixo para contextualizar a avaliação:",
                        "",
                        "Nome da empresa: ${company.name}",
                        "",
                        "Descrição da empresa: ${company.description}",
                        "",
                        "Dificuldade do desafio: ${company.difficulty} (1 = muito fácil, 10 = muito difícil)",
                        "",
                        "Expectativas da empresa nos funcionários: $companyExpectationsFormatted",
                        "",
                        "Situação simulada: $situation",
                        "",
                        "Resposta do usuário: $response",
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
                    )

                    val gemini = GeminiRequest()
                    val result = gemini.request(prompt)

                    if (result?.text() == null) {
                        res.setColor(Colors.DANGER)
                            .setText("❌ Ocorreu um erro ao processar sua requisição!")
                            .send(hook)
                        return@transaction
                    }

                    var text = result.text()!!.trim()

                    // Remove bloco de código se existir
                    if (text.startsWith("```json")) {
                        text = text.substring(7)
                    }
                    if (text.startsWith("```")) {
                        text = text.substring(3)
                    }
                    if (text.endsWith("```")) {
                        text = text.dropLast(3)
                    }
                    text = text.trim()

                    // Parse da resposta do Gemini
                    val mapper = ObjectMapper()
                    val jsonResponse = mapper.readTree(text)

                    val bonus = jsonResponse.get("bonus").asInt()
                    val reason = jsonResponse.get("reason").asText()

                    // Calcular salário base
                    val baseWage = contract.salary
                    val wageMultiplier = BigDecimal.ONE.add(BigDecimal.valueOf(0.1 * bonus))
                    var payValue = baseWage!!.multiply(wageMultiplier)

                    // Aplicar bônus do pet no salário
                    if (workWageBonus != null && petRarity != null) {
                        val petMultiplier = calculateSkillBonus(petRarity, workWageBonus.level!!)
                        payValue = payValue.add(baseWage.multiply(BigDecimal.valueOf(petMultiplier)))
                    }

                    // Calcular XP
                    val xpGain = if (bonus < 0) {
                        (Math.random() * 11).toInt() * -1 // -0 a -10
                    } else if (bonus == 0) {
                        (Math.random() * 11).toInt() // 0 a 10
                    } else {
                        (Math.random() * 51).toInt() + 10 // 10 a 60
                    }

                    // Aplicar bônus do pet no XP
                    if (workXpBonus != null && petRarity != null) {
                        val multiplier = calculateSkillBonus(petRarity, workXpBonus.level!!)
                        (xpGain * multiplier).toInt()
                    }

                    // Atualizar usuário
                    tx.update(userTable)
                        .set(userTable.MONEY, userTable.MONEY.add(payValue))
                        .set(userTable.XP, userTable.XP.add(BigDecimal.valueOf(xpGain.toLong())))
                        .where(userTable.ID.eq(event.user.id))
                        .execute()

                    // Buscar dados atualizados para mostrar
                    val updatedUser = tx.selectFrom(userTable)
                        .where(userTable.ID.eq(event.user.id))
                        .fetchOne()

                    // Enviar resposta baseada no bônus
                    val formattedPayValue = formatNumber(payValue.toDouble())
                    formatNumber(updatedUser!!.money!!.toDouble())

                    if (bonus < 0) {
                        res.setColor(Colors.DANGER)
                            .setText("😢 Sua resposta foi insatisfatória, por isso recebeu menos! Valor recebido: **Ꞩ $formattedPayValue**\n\n**Avaliação:** $reason")
                            .send(hook)
                    } else if (bonus > 0) {
                        res.setColor(Colors.SUCCESS)
                            .setText("🎉 Sua resposta foi satisfatória, por isso recebeu mais! Valor recebido: **Ꞩ $formattedPayValue**\n\n**Avaliação:** $reason")
                            .send(hook)
                    } else {
                        res.setColor(Colors.PRIMARY)
                            .setText("✅ Sua resposta foi neutra, por isso recebeu o mesmo salário! Valor recebido: **Ꞩ $formattedPayValue**\n\n**Avaliação:** $reason")
                            .send(hook)
                    }

                    handlerUserResponse(
                        response,
                        situation,
                        contract.id!!,
                        hook,
                        tx,
                        company
                    )

                    val willEndIn = LocalDateTime.now(ZoneOffset.UTC).plusHours(2)

                    val cooldownsTable = COOLDOWN

                    tx.insertInto(cooldownsTable)
                        .set(cooldownsTable.USERID, event.user.id)
                        .set(cooldownsTable.NAME, "work")
                        .set(cooldownsTable.TIMESTAMP, LocalDateTime.now(ZoneOffset.UTC))
                        .set(cooldownsTable.WILLENDIN, willEndIn)
                        .onConflict(cooldownsTable.USERID, cooldownsTable.NAME) // Campos da chave única
                        .doUpdate()
                        .set(cooldownsTable.TIMESTAMP, LocalDateTime.now(ZoneOffset.UTC))
                        .set(cooldownsTable.WILLENDIN, willEndIn)
                        .execute()
                } catch (e: Exception) {
                    e.printStackTrace()
                    res.setColor(Colors.DANGER)
                        .setText("❌ Ocorreu um erro ao processar sua resposta. Tente novamente.")
                        .send(hook)
                }
            }
        }
    }

    private fun handlerUserResponse(
        answer: String, response: String,
        contractId: Int, hook: InteractionHook, tx: DSLContext,
        company: CompanyRecord
    ) {
        tx.insertInto(WORKCHALLENGES)
            .set(WORKCHALLENGES.RESPONSE, response)
            .set(WORKCHALLENGES.CHALLENGE, answer)
            .set(WORKCHALLENGES.CONTRACTID, contractId)
            .set(WORKCHALLENGES.CREATEDAT, LocalDateTime.now())
            .execute()

        val workchallenges = tx.selectFrom(WORKCHALLENGES)
            .where(WORKCHALLENGES.CONTRACTID.eq(contractId))
            .fetch()

        val alreadyHasAnalyzed = getBlocking("$contractId-analyzed") != null

        if (alreadyHasAnalyzed) {
            return
        }

        val now = LocalDateTime.now()

        // Desafios da ultima semana
        val lastChallenges = workchallenges.stream()
            .filter { c -> c.createdat != null && c.createdat!!.isAfter(now.minusWeeks(1)) }
            .collect(Collectors.toList())

        if (lastChallenges.size > 2) {
            // Formatar expectativas da empresa
            val companyExpectationsFormatted = formatExpectations(company.expectations)

            // Construir histórico de desafios e respostas
            val challengesHistory = StringBuilder()
            for (i in lastChallenges.indices) {
                val challenge = lastChallenges[i]
                challengesHistory.append("Desafio ").append(i + 1).append(":\n")
                challengesHistory.append("Situação: ").append(challenge.challenge).append("\n")
                challengesHistory.append("Resposta: ").append(challenge.response).append("\n\n")
            }

            val prompt = brBuilder(
                "Você é um gerente de RH analisando o desempenho de um funcionário com base em seus desafios de trabalho recentes.",
                "",
                "**Contexto da Empresa:**",
                "Nome: ${company.name}",
                "Descrição: ${company.description}",
                "Dificuldade esperada: ${company.difficulty}/10",
                "Expectativas: $companyExpectationsFormatted",
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
                "- `DECREMENT_SALARY`: Desempenho medíocre mas recuperável, necessidade de motivação adicional",
                "- `null`: Desempenho dentro do esperado, sem necessidade de ações drásticas",
                "",
                "**Regras importantes:**",
                "- Seja justo e baseie sua decisão apenas nas evidências fornecidas",
                "- A razão deve ser detalhada, profissional e construtiva, porém não deve ser muito grande.",
                "- Retorne APENAS o JSON, sem comentários adicionais ou formatação markdown"
            )

            try {
                val gemini = GeminiRequest()
                val result = gemini.request(prompt)

                if (result?.text() != null) {
                    var text = result.text()!!.trim()

                    // Limpar resposta
                    if (text.startsWith("```json")) {
                        text = text.substring(7)
                    }
                    if (text.startsWith("```")) {
                        text = text.substring(3)
                    }
                    if (text.endsWith("```")) {
                        text = text.dropLast(3)
                    }
                    text = text.trim()

                    // Parse da resposta
                    val mapper = ObjectMapper()
                    val analysisResult = mapper.readTree(text)

                    val action = if (analysisResult.has("action") && !analysisResult.get("action").isNull)
                        analysisResult.get("action").asText()
                    else
                        null
                    val reason = analysisResult.get("reason").asText()

                    // Aplicar ação baseada na análise
                    if (action != null) {
                        val contractTable = CONTRACT

                        when (action.uppercase(Locale.getDefault())) {
                            "FIRED" -> {
                                // Demitir funcionário
                                tx.update(contractTable)
                                    .set(contractTable.STATUS, Contractstatus.FIRED)
                                    .set(contractTable.UPDATEDAT, LocalDateTime.now())
                                    .where(contractTable.ID.eq(contractId))
                                    .execute()

                                // Limpar contractId do usuário
                                val userTable = USER
                                tx.update(userTable)
                                    .set(userTable.CONTRACTID, null as Int?)
                                    .where(userTable.CONTRACTID.eq(contractId))
                                    .execute()

                                // Enviar mensagem de demissão
                                res.setColor(Colors.DANGER)
                                    .setText(
                                        "💼 **Aviso Importante**\n\n" +
                                                "Com base na análise do seu desempenho nos últimos desafios, a empresa tomou a decisão de encerrar seu contrato.\n\n" +
                                                "**Motivo:** $reason\n\n" +
                                                "Seu contrato foi finalizado."
                                    )
                                    .send(hook)
                            }

                            "INCREMENT_SALARY" -> {
                                // Aumentar salário em 20%
                                val currentSalary = tx.select(contractTable.SALARY)
                                    .from(contractTable)
                                    .where(contractTable.ID.eq(contractId))
                                    .fetchOneInto(BigDecimal::class.java)

                                val newSalary = currentSalary!!.multiply(BigDecimal.valueOf(1.2))

                                tx.update(contractTable)
                                    .set(contractTable.SALARY, newSalary)
                                    .set(contractTable.UPDATEDAT, LocalDateTime.now())
                                    .where(contractTable.ID.eq(contractId))
                                    .execute()

                                res.setColor(Colors.SUCCESS)
                                    .setText(
                                        "🎉 **Parabéns! Aumento de Salário**\n\n" +
                                                "Com base no seu excelente desempenho nos últimos desafios, a empresa decidiu aumentar seu salário em 20%!\n\n" +
                                                "**Novo salário:** ${formatNumber(newSalary.toDouble())}\n" +
                                                "**Motivo:** $reason"
                                    )
                                    .send(hook)
                            }

                            "DECREMENT_SALARY" -> {
                                // Reduzir salário em 15%
                                val currentSalary2 = tx.select(contractTable.SALARY)
                                    .from(contractTable)
                                    .where(contractTable.ID.eq(contractId))
                                    .fetchOneInto(BigDecimal::class.java)

                                val newSalary2 = currentSalary2!!.multiply(BigDecimal.valueOf(0.85))

                                tx.update(contractTable)
                                    .set(contractTable.SALARY, newSalary2)
                                    .set(contractTable.UPDATEDAT, LocalDateTime.now())
                                    .where(contractTable.ID.eq(contractId))
                                    .execute()

                                res.setColor(Colors.WARNING)
                                    .setText(
                                        "⚠️ **Ajuste de Salário**\n\n" +
                                                "Com base na análise do seu desempenho, a empresa fez um ajuste no seu salário.\n\n" +
                                                "**Novo salário:** ${formatNumber(newSalary2.toDouble())}\n" +
                                                "**Motivo:** $reason\n\n" +
                                                "Esperamos ver melhorias no seu desempenho futuro."
                                    )
                                    .send(hook)
                            }

                            else ->                                 // Nenhuma ação - desempenho normal
                                res.setColor(Colors.PRIMARY)
                                    .setText(
                                        "📊 **Análise de Desempenho**\n\n" +
                                                "Sua performance nos últimos desafios foi analisada pela equipe de RH.\n\n" +
                                                "**Feedback:** $reason\n\n" +
                                                "Continue com o bom trabalho!"
                                    )
                                    .send(hook)
                        }

                        setBlocking("$contractId-analyzed", "true", 3, TimeUnit.DAYS)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Em caso de erro na análise, apenas continue sem ações
                System.err.println("Erro na análise de desempenho: ${e.message}")
            }
        }
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
}
