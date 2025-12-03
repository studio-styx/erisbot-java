package studio.styx.erisbot.discord.features.events.message.trivia

import dev.minn.jda.ktx.coroutines.await
import games.tryvia.core.TryviaGame
import games.tryvia.dtos.TryviaMessage
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import shared.Cache
import studio.styx.erisbot.generated.enums.Tryviatypes

class WriteResponse {
    suspend fun execute(event: MessageReceivedEvent) {
        val channel = event.channel
        val user = event.member?.user ?: return
        val message = event.message

        if (user.isBot) return

        val game = Cache.get<TryviaGame>("tryvia:game:${channel.id}") ?: return

        val currentQuestion = game.getQuestions()[game.getCurrentQuestion()]

        if (currentQuestion.type == Tryviatypes.WRITEINCHAT) {
            if (game.getParticipants().find { it.id == user.id } == null) return

            game.setActualQuestionResponded(true)

            // Prepara lista de respostas possíveis
            val possibleResponses = buildList {
                // Adiciona resposta principal (se não for null ou vazia)
                currentQuestion.correctanswer?.takeIf { it.isNotBlank() }?.let { add(it) }

                // Adiciona variações (filtra nulos e strings vazias)
                currentQuestion.correctanswersvariation?.forEach { variation ->
                    variation?.takeIf { it.isNotBlank() }?.let { add(it) }
                }
            }

            // Se não houver respostas possíveis, retorna
            if (possibleResponses.isEmpty()) return

            // Normaliza todas as respostas possíveis
            val normalizedPossibleResponses = possibleResponses.map(::normalizeAnswer)

            // Normaliza a resposta do usuário
            val userAnswer = message.contentRaw
            val userNormalizedAnswer = normalizeAnswer(userAnswer)

            // Verifica correspondência (case-insensitive e sem acentos)
            val isCorrect = normalizedPossibleResponses.any {
                it == userNormalizedAnswer
            }

            if (isCorrect) {
                val checkEmoji = Emoji.fromUnicode("✅")
                message.addReaction(checkEmoji).await()
                game.sendSuccefullyAnsweredMessage(channel, user.id)
            } else {
                val xEmoji = Emoji.fromUnicode("❌")
                message.addReaction(xEmoji).await()
            }
        }
    }

    private fun normalizeAnswer(answer: String): String {
        return answer.trim()
            .lowercase() // ou .lowercase(Locale.getDefault()) se quiser localização
            .replace(Regex("\\s+"), " ") // espaços múltiplos para um único
            .replace(Regex("[\\s\\-_]"), "") // remove espaços, hífens e underlines
            .replace(Regex("[áàãâä]"), "a")
            .replace(Regex("[éèêë]"), "e")
            .replace(Regex("[íìîï]"), "i")
            .replace(Regex("[óòõôö]"), "o")
            .replace(Regex("[úùûü]"), "u")
            .replace(Regex("[ç]"), "c")
            .replace(Regex("[ñ]"), "n")
            .replace(Regex("[^a-z0-9]"), "") // remove qualquer caractere que não seja letra ou número
    }

}