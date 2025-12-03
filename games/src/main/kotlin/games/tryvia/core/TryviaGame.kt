package games.tryvia.core

import dev.minn.jda.ktx.coroutines.await
import games.tryvia.dtos.TryviaMessage
import games.tryvia.dtos.TryviaParticipant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.components.MessageTopLevelComponent
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion
import org.jooq.DSLContext
import shared.Cache
import shared.Colors
import shared.utils.Icon
import shared.utils.MentionUtil
import shared.utils.Utils
import studio.styx.erisbot.generated.enums.Tryviadifficulty
import studio.styx.erisbot.generated.enums.Tryviatypes
import studio.styx.erisbot.generated.tables.records.TryviaquestionsRecord
import studio.styx.erisbot.generated.tables.references.GUILDMEMBER
import studio.styx.erisbot.generated.tables.references.GUILDSETTINGS
import utils.ComponentBuilder

class TryviaGame(
    private val ownerId: String,
    private val questions: MutableList<TryviaquestionsRecord>,
    private val participants: MutableList<TryviaParticipant>,
    private val dsl: DSLContext,
    private var currentQuestion: Int,
    private var actualQuestionResponded: Boolean = false,
    private var consecutiveNoResponse: Int = 0
) {
    var createdAt = System.currentTimeMillis()
        private set
    var messages: MutableList<TryviaMessage> = mutableListOf()
    private var timeoutJob: Job? = null

    private val MIN_PARTICIPANTS_FOR_STATS = 2
    private val MIN_POINTS_FOR_STATS = 5
    private val MIN_GAME_DURATION = 60 // segundos
    private val MIN_POINTS_THRESHOLD = 10

    fun getOwnerId(): String { return ownerId }
    fun getQuestions(): MutableList<TryviaquestionsRecord> { return questions }
    fun getParticipants(): MutableList<TryviaParticipant> { return participants }
    fun getCurrentQuestion(): Int { return currentQuestion }
    fun getActualQuestionResponded(): Boolean { return actualQuestionResponded }
    fun getConsecutiveNoResponse(): Int { return consecutiveNoResponse }

    fun addQuestion(question: TryviaquestionsRecord) = apply {
        questions.add(question)
    }

    fun addParticipant(participant: TryviaParticipant) = apply {
        participants.add(participant)
    }

    fun setCurrentQuestion(currentQuestion: Int) = apply {
        this.currentQuestion = currentQuestion
    }

    fun addCurrentQuestion() = apply {
        currentQuestion++
    }

    fun setActualQuestionResponded(isResponded: Boolean) = apply {
        actualQuestionResponded = isResponded
    }

    fun setActualQuestionResponded() = apply {
        actualQuestionResponded = actualQuestionResponded == false
    }

    fun resetConsecutiveNoResponse() = apply {
        consecutiveNoResponse = 0
    }

    fun addConsecutiveNoResponse() = apply {
        consecutiveNoResponse++
    }

    fun getParticipantsByPointsOrder(): MutableList<TryviaParticipant> {
        return participants.sortedByDescending { it.points }.toMutableList()
    }

    fun nextQuestion(): TryviaquestionsRecord? {
        currentQuestion++
        return questions.getOrNull(currentQuestion)
    }

    private suspend fun deleteMessages() {
        val iterator = messages.iterator()

        while (iterator.hasNext()) {
            val message = iterator.next()

            if (message.turnToDelete <= 0) {
                // Se o tempo acabou, deleta do Discord
                try {
                    runCatching { message.message.delete().await() }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                // E remove da lista da memória
                iterator.remove()
            } else {
                // Se ainda tem turnos, apenas diminui o contador e MANTÉM na lista
                message.turnToDelete--
            }
        }
    }

    suspend fun sendQuestionMessage(channel: MessageChannelUnion) {
        timeoutJob?.cancel()
        deleteMessages()

        try {
            val message = channel.sendMessageComponents(questionMenu(this, false))
                .useComponentsV2()
                .await()
            messages.add(TryviaMessage(message))
        } catch (e: Exception) {
            e.printStackTrace()
        }

        actualQuestionResponded = false
        val questionIndexWhenSent = currentQuestion

        // Inicia o timeout (só se não foi respondida ainda)
        timeoutJob = CoroutineScope(Dispatchers.Default).launch {
            delay(20000)

            if (currentQuestion == questionIndexWhenSent && !actualQuestionResponded) {
                CoroutineScope(Dispatchers.Default).launch {
                    handleQuestionTimeout(channel)
                }
            }
        }
    }

    suspend fun sendSuccefullyAnsweredMessage(channel: MessageChannelUnion, userId: String) {
        timeoutJob?.cancel()

        deleteMessages()

        val question = questions[currentQuestion]
        val pointsReward = when (question.difficulty) {
            Tryviadifficulty.EASY -> 1
            Tryviadifficulty.MEDIUM -> 2
            Tryviadifficulty.HARD -> 3
            else -> 2
        }
        val participant = participants.find { it.id == userId } ?: run {
            participants.add(TryviaParticipant(userId))
            participants.last()
        }

        participant.points += pointsReward
        participant.streak++
        setActualQuestionResponded(false)

        for (p in participants) {
            if (p.id == userId) continue
            p.streak = 0
        }

        val message = channel.sendMessageComponents(
            ComponentBuilder.ContainerBuilder.create()
                .withColor(Colors.SUCCESS)
                .addText("${Icon.static.get("Eris_happy")} | Parabéns ${MentionUtil.userMention(userId)}! Você acertou a pergunta em 20 segundos! streak: **${participant.streak}**")
                .build(),
            ComponentBuilder.ContainerBuilder.create()
                .withColor(Colors.FUCHSIA)
                .addText("Explicação: **${question.explanation}**")
                .build()
        ).useComponentsV2().await()
        messages.add(TryviaMessage(message, false, 1))

        val nextQuestion = nextQuestion()

        delay(5000)

        if (nextQuestion == null) {
            handleTryviaEnd(channel, "${Icon.static.get("Eris_cry")} | As perguntas acabaram!")
            return
        }

        sendIntervalMessage(channel)
        sendQuestionMessage(channel)
    }

    suspend fun sendIntervalMessage(channel: MessageChannelUnion) {
        timeoutJob?.cancel()

        deleteMessages()

        // Agora envia a nova mensagem
        try {
            val top1UserId = getParticipantsByPointsOrder().first().id

            val top1User = channel.jda.getUserById(top1UserId) ?: channel.jda.retrieveUserById(top1UserId).await()

            val message = channel.sendMessageComponents(intervalMenu(this, top1User.avatarUrl ?: top1User.effectiveAvatarUrl)).useComponentsV2().await()
            messages.add(TryviaMessage(message))
        } catch (e: Exception) {
            e.printStackTrace()
        }

        delay(10000)
    }

    suspend fun handleQuestionTimeout(channel: MessageChannelUnion) {
        if (actualQuestionResponded) return
        if (currentQuestion >= questions.size) {
            handleTryviaEnd(channel , "${Icon.static.get("Eris_cry")} | As perguntas acabaram!")
            return
        }

        addConsecutiveNoResponse()

        val message = channel.sendMessageComponents(
            ComponentBuilder.ContainerBuilder.create()
                .withColor(Colors.DANGER)
                .addText("Ninguém acertou ou respondeu a pergunta em 20 segundos! Streaks zerados.")
                .build(),
            ComponentBuilder.ContainerBuilder.create()
                .withColor(Colors.FUCHSIA)
                .addText("A resposta correta era: **${questions[currentQuestion].correctanswer}**. \n **Explicação:** ${questions[currentQuestion].explanation}")
                .build()
        ).useComponentsV2().await()
        messages.add(TryviaMessage(message, false, 1))

        // Avança para próxima pergunta
        val nextQuestion = nextQuestion()

        for (participant in participants) {
            participant.streak = 0
        }

        if (!actualQuestionResponded) addConsecutiveNoResponse()

        if (consecutiveNoResponse > 4) {
            handleTryviaEnd(channel, "${Icon.static.get("error")} | Ninguem respondeu a 3 perguntas consecutivas, por isso o jogo foi finalizado")
            return
        }

        if (nextQuestion == null) {
            handleTryviaEnd(channel, "${Icon.static.get("Eris_cry")} | As perguntas acabaram após ninguem responder a ultima!")
            return
        }

        sendIntervalMessage(channel)
        sendQuestionMessage(channel)
    }

    suspend fun handleTryviaEnd(channel: MessageChannelUnion, message: String) {
        timeoutJob?.cancel()

        val iterator = messages.iterator()

        while (iterator.hasNext()) {
            val message = iterator.next()

            try {
                runCatching { message.message.delete().await() }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            iterator.remove()
        }

        Cache.remove("tryvia:game:${channel.id}")
        channel.sendMessageComponents(ComponentBuilder.ContainerBuilder.create().withColor(Colors.DANGER).addText(message).build()).useComponentsV2().await()
        processGameClosure(channel)
    }

    private suspend fun processGameClosure(channel: MessageChannelUnion) {

        // Verificar se há participantes válidos
        val validParticipants = getParticipants()
            .filter { it.id != getOwnerId() } // Prevenir autofarm do dono
            .filter { it.points > 0 } // Só participantes com pontos

        if (validParticipants.isEmpty()) {
            channel.sendMessageComponents(
                ComponentBuilder.ContainerBuilder.create()
                    .addText("${Icon.static.get("info")} | Jogo encerrado sem participantes válidos.")
                    .withColor(Colors.PRIMARY)
                    .build()
            ).useComponentsV2().await()
            return
        }

        // Ordenar participantes por pontos
        val participantsByPoints = getParticipantsByPointsOrder()

        // Validar se há pelo menos 1 participante com pontos
        if (participantsByPoints.isEmpty()) {
            return
        }

        val top1UserId = participantsByPoints[0].id

        // Obter usuário vencedor com fallback
        val top1User = try {
            channel.jda.getUserById(top1UserId) ?: channel.jda.retrieveUserById(top1UserId).await()
        } catch (e: Exception) {
            channel.jda.selfUser // Fallback
        }

        channel.sendMessageComponents(tryviaEndGameMenu(this, top1User.effectiveAvatarUrl))
            .useComponentsV2()
            .await()
        // Atualizar estatísticas apenas se condições forem atendidas
        if (shouldUpdateStats(this, validParticipants)) {
            updateGameStatistics(this, top1UserId, channel.asTextChannel().guild.id)
        }
    }

    private fun updateGameStatistics(
        game: TryviaGame,
        top1UserId: String?,
        guildId: String?
    ) {
        if (guildId == null) return

        val participants = game.getParticipants()

        // Filtros adicionais para prevenir abuso
        val eligibleParticipants = participants.filter { participant ->
            participant.points >= MIN_POINTS_FOR_STATS && // Pontuação mínima
                    participant.id != game.getOwnerId() && // Não é o dono
                    !isSuspiciousParticipant(game, participant) // Verificação adicional
        }

        if (eligibleParticipants.size < MIN_PARTICIPANTS_FOR_STATS) {
            return // Não atualiza stats se poucos participantes válidos
        }

        dsl.transaction { config ->
            val tx = config.dsl()

            // Garantir que guild existe
            tx.insertInto(GUILDSETTINGS)
                .set(GUILDSETTINGS.ID, guildId)
                .onDuplicateKeyIgnore()
                .execute()

            // Batch otimizado com chunking para muitos participantes
            val chunks = eligibleParticipants.chunked(50)

            chunks.forEach { chunk ->
                val batchQueries = chunk.map { participant ->
                    val isWinner = participant.id == top1UserId

                    tx.insertInto(GUILDMEMBER)
                        .set(GUILDMEMBER.ID, participant.id)
                        .set(GUILDMEMBER.GUILDID, guildId)
                        .set(GUILDMEMBER.TRYVIAGAMES, 1)
                        .set(GUILDMEMBER.TRYVIAPOINTS, participant.points)
                        .set(GUILDMEMBER.TRYVIAWINS, if (isWinner) 1 else 0)
                        .onDuplicateKeyUpdate()
                        .set(GUILDMEMBER.TRYVIAGAMES, GUILDMEMBER.TRYVIAGAMES.add(1))
                        .set(GUILDMEMBER.TRYVIAPOINTS, GUILDMEMBER.TRYVIAPOINTS.add(participant.points))
                        .apply {
                            if (isWinner) {
                                set(GUILDMEMBER.TRYVIAWINS, GUILDMEMBER.TRYVIAWINS.add(1))
                            }
                        }
                }

                if (batchQueries.isNotEmpty()) {
                    tx.batch(batchQueries).execute()
                }
            }
        }
    }

    private fun shouldUpdateStats(game: TryviaGame, validParticipants: List<TryviaParticipant>): Boolean {
        val time = (System.currentTimeMillis() - game.createdAt) / 1000 // em segundos

        return validParticipants.size > 1 && // Mais de 1 participante
                time > MIN_GAME_DURATION && // Jogo durou tempo mínimo
                validParticipants.any { it.points > MIN_POINTS_THRESHOLD } // Pontuação significativa
    }

    private fun isSuspiciousParticipant(game: TryviaGame, user: TryviaParticipant): Boolean {
        // 1. Verificar tempo mínimo de jogo (anti-farm rápido)
        val gameDurationSeconds = (System.currentTimeMillis() - user.createdAt) / 1000
        if (gameDurationSeconds < 60) {
            return true // Jogou menos de 1 minuto - muito suspeito
        }

        val totalAnswers = user.correctAnswers.size + user.incorrectAnswers.size
        if (totalAnswers == 0) return false

        val gameTotalQuestions = game.getQuestions().size
        val gameDurationMinutes = (System.currentTimeMillis() - game.createdAt) / (1000 * 60)

        // Se o jogo durou pouco, exigir menos respostas
        val expectedAnswers = when {
            gameDurationMinutes < 2 -> 2 // Jogo muito curto
            gameDurationMinutes < 5 -> gameTotalQuestions / 4 // Jogo curto
            else -> gameTotalQuestions / 3 // Jogo normal
        }.coerceIn(2, gameTotalQuestions) // Entre 2 e total de perguntas

        // Penalizar quem respondeu muito menos que o esperado
        val participationRate = totalAnswers.toDouble() / expectedAnswers
        if (participationRate < 0.3 && totalAnswers > 0) { // Menos de 30% do esperado
            return true
        }

        // 2. Verificar proporção de acertos considerando quantidade e dificuldade
        val accuracy = user.correctAnswers.size.toDouble() / totalAnswers

        // Contar questões por dificuldade
        val easyCorrect = user.correctAnswers.count { it.difficulty == Tryviadifficulty.EASY }
        val mediumCorrect = user.correctAnswers.count { it.difficulty == Tryviadifficulty.MEDIUM }
        val hardCorrect = user.correctAnswers.count { it.difficulty == Tryviadifficulty.HARD }

        val easyIncorrect = user.incorrectAnswers.count { it.difficulty == Tryviadifficulty.EASY }
        val mediumIncorrect = user.incorrectAnswers.count { it.difficulty == Tryviadifficulty.MEDIUM }
        val hardIncorrect = user.incorrectAnswers.count { it.difficulty == Tryviadifficulty.HARD }

        val totalEasy = easyCorrect + easyIncorrect
        val totalMedium = mediumCorrect + mediumIncorrect
        val totalHard = hardCorrect + hardIncorrect

        // Verificação de acerto quase perfeito considerando contexto
        if (accuracy > 0.95) {
            // Se tiver poucas perguntas e maioria fácil, pode ser normal
            val isMostlyEasy = totalEasy >= totalAnswers * 0.7 // 70%+ são fáceis

            // Apenas marcar como suspeito se:
            // - Tem muitas perguntas (>15) E alta precisão
            // - OU tem precisão perfeita (100%) com perguntas médias/difíceis
            if (totalAnswers > 15 ||
                (accuracy == 1.0 && totalHard + totalMedium >= 5)) {
                return true
            }
        }

        // 3. Padrão suspeito: Muitas erradas fáceis (farmando erros rápidos)
        if (easyIncorrect >= 5 && totalEasy > 0) {
            val easyErrorRate = easyIncorrect.toDouble() / totalEasy
            if (easyErrorRate > 0.8) { // Errou 80%+ das fáceis
                return true
            }
        }

        // 4. Padrão suspeito: Acertou muitas difíceis mas errou fáceis
        if (hardCorrect >= 3 && easyCorrect == 0 && totalEasy >= 2) {
            // Acertou 3+ difíceis mas errou todas as fáceis (inconsistência)
            return true
        }

        // 5. Padrão suspeito: Streak muito alto considerando dificuldade
        if (user.streak > 20) {
            // Verificar se o streak inclui perguntas difíceis
            val hasHardInStreak = user.correctAnswers.takeLast(user.streak.coerceAtMost(20))
                .any { it.difficulty == Tryviadifficulty.HARD }

            // Streak de 20+ sem nenhuma difícil é suspeito
            if (!hasHardInStreak) {
                return true
            }
        }

        // 6. Padrão suspeito: Perfil de resposta muito diferente dos outros participantes
        if (game.getParticipants().size > 1) {
            val otherParticipants = game.getParticipants()
                .filter { it.id != user.id }
                .filter { it.correctAnswers.isNotEmpty() || it.incorrectAnswers.isNotEmpty() }

            if (otherParticipants.isNotEmpty()) {
                val avgOthersAccuracy = otherParticipants
                    .map { it.correctAnswers.size.toDouble() / (it.correctAnswers.size + it.incorrectAnswers.size) }
                    .average()

                val avgOthersEasyRate = otherParticipants
                    .map { p ->
                        p.correctAnswers.count { it.difficulty == Tryviadifficulty.EASY }.toDouble() /
                                (p.correctAnswers.size + p.incorrectAnswers.size)
                    }
                    .average()

                val userEasyRate = easyCorrect.toDouble() / totalAnswers

                // Se o usuário tem precisão muito acima da média E resolveu menos fáceis
                if (accuracy > avgOthersAccuracy * 1.5 && userEasyRate < avgOthersEasyRate * 0.5) {
                    return true
                }
            }
        }

        return false
    }
}

fun intervalMenu(game: TryviaGame, top1UserAvatar: String): MutableList<MessageTopLevelComponent> {
    val ranking = game.getParticipantsByPointsOrder()
    val expiresAt = System.currentTimeMillis() + 10000

    val rankingFormated = ranking.mapIndexed { index, player ->
        "**${index + 1}.** - **<@${player.id}>** - **${player.points}** pontos"
    }.joinToString("\n")

    return mutableListOf(
        ComponentBuilder.ContainerBuilder.create()
            .addText("# Intervalo")
            .addDivider()
            .addSection(top1UserAvatar, Utils.brBuilder(
                "Próxima pergunta <t:${expiresAt / 1000}:R>",
                "Aproveite esse tempo para descansar, e pensar sobre a próxima pergunta.",
                "## Melhores usuários:",
                rankingFormated
            ))
            .withColor(Colors.AZOXO)
            .build()
    )
}

fun questionMenu(game: TryviaGame, disableButtons: Boolean = false): MutableList<MessageTopLevelComponent> {
    val item = game.getQuestions()[game.getCurrentQuestion()]
    val result = mutableListOf<MessageTopLevelComponent>()

    // Criar container
    val containerBuilder = ComponentBuilder.ContainerBuilder.create()

    // Título baseado na posição da pergunta
    val title = when (game.getCurrentQuestion()) {
        0 -> "Primeira pergunta"
        game.getQuestions().size - 1 -> "Última pergunta"
        else -> "Pergunta ${game.getCurrentQuestion() + 1}"
    }

    // Adicionar conteúdo ao container
    containerBuilder
        .addText(Utils.brBuilder("### $title"))
        .addText(Utils.brBuilder(
            "# Pergunta:",
            "## ${item.question}",
            ""
        ))

    // Processar alternativas e botões baseado no tipo
    when (item.type) {
        Tryviatypes.MULTIPLE -> {
            val alternatives = mutableListOf<String>().apply {
                item.incorrectanswers?.let { addAll(it.filterNotNull()) }
                item.correctanswer?.let { add(it) }
            }

            val shuffledAlternatives = alternatives.shuffled()
            val alternativesText = shuffledAlternatives.mapIndexed { index, alt ->
                val letter = ('A'.code + index).toChar()
                "$letter. $alt"
            }.joinToString("\n")

            containerBuilder.addText(Utils.brBuilder("# Alternativas:", alternativesText))

            // Criar botões usando padrão JDA
            val buttons = shuffledAlternatives.mapIndexed { index, alternative ->
                val letter = ('A'.code + index).toChar()
                val isCorrect = alternative == item.correctanswer
                Button.primary(
                    "tryvia/game/multiple/${isCorrect}/$letter",
                    letter.toString()
                ).withDisabled(disableButtons)
            }

            // Adicionar ActionRow usando padrão JDA
            if (buttons.isNotEmpty()) {
                result.add(ActionRow.of(buttons))
            }
        }

        Tryviatypes.BOOLEAN -> {
            containerBuilder.addText(Utils.brBuilder("# Responda com verdadeiro ou falso:"))

            val correctBoolean = item.correct ?: when(item.correctanswer) {
                "true", "sim", "verdadeiro" -> true
                else -> false
            }

            // Criar botões Verdadeiro/Falso usando padrão JDA
            val trueButton = Button.success(
                "tryvia/game/boolean/${if (correctBoolean) "correct" else "incorrect"}",
                "Verdadeiro"
            ).withDisabled(disableButtons)

            val falseButton = Button.danger(
                "tryvia/game/boolean/${if (!correctBoolean) "correct" else "incorrect"}",
                "Falso"
            ).withDisabled(disableButtons)

            // Adicionar ActionRow
            result.add(ActionRow.of(trueButton, falseButton))
        }

        Tryviatypes.WRITEINCHAT -> {
            containerBuilder.addText(Utils.brBuilder("Escreva no chat a resposta correta:"))
        }

        else -> {
            containerBuilder.addText("# Tipo de pergunta não suportado: ${item.type}")
        }
    }

    // Formatar dificuldade usando enum Tryviatypes
    val difficultyText = when (item.difficulty) {
        Tryviadifficulty.EASY -> "Fácil"
        Tryviadifficulty.MEDIUM -> "Médio"
        Tryviadifficulty.HARD -> "Difícil"
        else -> "Desconhecida"
    }

    // Adicionar metadata
    containerBuilder.addText(Utils.brBuilder(
        "-# **id:** ${item.id} | **dificuldade**: $difficultyText"
    ))

    // Adicionar timer
    val expiresAt = System.currentTimeMillis() + 20000
    containerBuilder.addText(Utils.brBuilder(
        "",
        "Todos têm 20 segundos para responder! Expira <t:${expiresAt / 1000}:R>"
    ))
        .withColor(Colors.FUCHSIA)

    // Adicionar container no início da lista
    result.add(0, containerBuilder.build())

    return result
}

fun tryviaEndGameMenu(game: TryviaGame, thumbnail: String): MutableList<MessageTopLevelComponent> {
    val ranking = game.getParticipantsByPointsOrder()

    val result = ranking.mapIndexed { index, player ->
        "**${index + 1}.** - **<@${player.id}>** - **${player.points}** pontos"
    }.joinToString("\n")

    return mutableListOf(
        ComponentBuilder.ContainerBuilder.create()
            .addText("# Fim de jogo")
            .addDivider()
            .addText(Utils.brBuilder(
                if (game.getQuestions().size > 5)
                    "Tivemos várias perguntas até chegarmos aqui"
                else "Tivemos algumas perguntas até chegarmos aqui",
                "Perguntas difíceis e fáceis, e ${game.getParticipants().size} participantes!",
                "Com isso, o nosso ranking ficou assim:"
            ))
            .addDivider()
            .addSection(thumbnail, result)
            .build()
    )
}