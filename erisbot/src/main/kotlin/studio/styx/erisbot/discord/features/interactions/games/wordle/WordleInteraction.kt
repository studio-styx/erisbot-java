package studio.styx.erisbot.discord.features.interactions.games.wordle

import database.dtos.log.CreateLogData
import database.extensions.createLog
import dev.minn.jda.ktx.coroutines.await
import kotlinx.coroutines.delay
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.components.label.Label
import net.dv8tion.jda.api.components.textinput.TextInput
import net.dv8tion.jda.api.components.textinput.TextInputStyle
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.modals.Modal
import net.dv8tion.jda.api.utils.FileUpload
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import shared.Cache
import shared.Colors
import shared.utils.CustomIdHelper
import shared.utils.Icon
import studio.styx.erisbot.core.dtos.wordle.WordleGameDto
import studio.styx.erisbot.core.exceptions.InteractionUsedByUnauthorizedUserException
import discord.extensions.jda.reply.rapidContainerEdit
import discord.extensions.jda.reply.rapidContainerReply
import studio.styx.erisbot.core.interfaces.ResponderInterface
import studio.styx.erisbot.functions.imageGenerator.wordle.WordleImageGenerator
import utils.ComponentBuilder
// Importe o seu gerador de imagem aqui (assumindo o nome do exemplo anterior)
// import studio.styx.erisbot.functions.helpers.wordle.WordleImageGenerator
import java.time.LocalDateTime

@Component
class WordleInteraction : ResponderInterface {
    @Autowired
    lateinit var dsl: DSLContext

    override val customId = "wordle/:action/:userId"

    override suspend fun execute(event: ButtonInteractionEvent) {
        val params = CustomIdHelper(customId, event.customId)
        val action = params.get("action")
        val userId = params.get("userId")

        if (userId != event.user.id) throw InteractionUsedByUnauthorizedUserException(userId)

        val key = "wordle:$userId"
        when (action) {
            "deleteGame" -> {
                val gameToDelete = Cache.get<WordleGameDto>(key)
                if (gameToDelete == null) {
                    event.rapidContainerEdit(Colors.DANGER, "${Icon.static.get("denied")} | Você não tem nenhum jogo de termo em andamento!")
                    return
                }
                Cache.remove(key)
                event.rapidContainerEdit(Colors.DANGER, "${Icon.static.get("success")} | Jogo cancelado com sucesso")
            }
            "writeWord" -> {
                val game = Cache.get<WordleGameDto>(key)
                if (game == null) {
                    event.rapidContainerEdit(
                        Colors.DANGER,
                        "${Icon.static.get("denied")} | O jogo expirou ou não existe mais.",
                    )
                    return
                }

                // Verifica se o jogo já acabou antes de abrir o modal
                if (game.isOver) {
                    event.rapidContainerEdit(Colors.DANGER, "${Icon.static.get("denied")} | Esta partida já acabou!")
                    return
                }

                val answer = TextInput.create("response", TextInputStyle.PARAGRAPH)
                    .setPlaceholder("Digite a palavra aqui.")
                    .setMinLength(game.word.length)
                    .setMaxLength(game.word.length)
                    .build()
                val modal = Modal.create(
                    event.customId,
                    "Termo"
                ).addComponents(Label.of("Palavra", answer))
                    .build()

                event.replyModal(modal).queue()
            }
        }
    }

    override suspend fun execute(event: ModalInteractionEvent) {
        event.deferReply(false).queue()

        val userId = event.user.id
        val key = "wordle:$userId"
        val game = Cache.get<WordleGameDto>(key)

        if (game == null) {
            event.rapidContainerEdit(Colors.DANGER, "${Icon.static.get("denied")} | Jogo não encontrado ou expirado.")
            return
        }

        val response = event.getValue("response")?.asString?.lowercase()?.trim() ?: ""

        if (response.length != game.word.length) {
            event.rapidContainerEdit(Colors.DANGER, "${Icon.static.get("denied")} | A palavra deve ter exatamente **${game.word.length}** letras.")
            return
        }

        val currentAttempts = game.attempts.toMutableList().apply { add(response) }

        val imageBytes = WordleImageGenerator.createWordleImage(game.word, currentAttempts)
        val fileUpload = FileUpload.fromData(imageBytes, "wordle.png")

        // Atualiza estado do jogo
        game.attempts.add(response)
        game.lastAttemptAt = LocalDateTime.now()

        // Lógica de Comparação
        val normalizedResponse = normalizeWord(response, game.word)
        val normalizedTarget = normalizeWord(game.word, game.word)

        // --- CENÁRIO DE VITÓRIA ---
        if (normalizedResponse == normalizedTarget) {
            game.isOver = true
            game.isWon = true
            // game.endedAt = LocalDateTime.now() // Se tiver esse campo no DTO

            // Envia mensagem de parabéns
            event.rapidContainerReply(
                Colors.SUCCESS,
                "${Icon.static.get("success")} | Parabéns! Você adivinhou a palavra **${game.word.uppercase()}** corretamente em **${game.attempts.size}** tentativas!"
            )

            // Edita a mensagem original (onde estava o botão) com a imagem final e botão desativado
            event.message?.editMessageAttachments(fileUpload)?.setComponents(
                ActionRow.of(
                    Button.success("wordle/writeWord/$userId", "Fim de jogo").asDisabled()
                )
            )?.queue()

            // Limpeza e Logs
            Cache.remove(key)
            saveGameResult(event, game, "won")
            return
        }

        // --- CENÁRIO DE DERROTA ---
        if (game.attempts.size >= game.maxAttempts) {
            game.isOver = true
            game.isWon = false
            // game.endedAt = LocalDateTime.now()

            event.hook.sendMessage(
                "${Icon.static.get("denied")} | Suas tentativas acabaram! A palavra correta era **${game.word.uppercase()}**."
            ).queue()

            event.message?.editMessageAttachments(fileUpload)?.setComponents(
                ActionRow.of(
                    Button.success("wordle/writeWord/$userId", "Fim de jogo").asDisabled()
                )
            )?.queue()

            Cache.remove(key)
            saveGameResult(event, game, "lose")
            return
        }

        Cache.set(key, game, 1800)

        // Edita a mensagem original com a nova imagem atualizada
        event.message?.editMessageAttachments(fileUpload)?.queue()

        val msg = event.hook.sendMessageComponents(
            ComponentBuilder.ContainerBuilder.create().withColor(Colors.SUCCESS).addText(
                "${Icon.static.get("success")} | Tentativa registrada! Você tem mais **${game.maxAttempts - game.attempts.size}** tentativas."
            ).build()
        ).useComponentsV2().setEphemeral(true).await()

        delay(2500)
        msg.delete().queue()
    }

    private fun saveGameResult(event: ModalInteractionEvent, game: WordleGameDto, status: String) {
        val message = if (status == "won")
                "Ganhou a partida de termo no servidor **${event.guild?.name}** em **${game.attempts.size}** tentativas (palavra: ${game.word})"
        else
            "Perdeu uma partida de termo no servidor **${event.guild?.name}** tentativas: **${game.attempts.size}** (palavra: ${game.word})"
        dsl.createLog(CreateLogData(
            userId = event.user.id,
            message = message,
            level = 3,
            tags = listOf("wordle", status)
        )).safeInsertWithUser()
    }

    private fun normalizeLetter(letter: String): String {
        val map = mapOf(
            "á" to "a", "à" to "a", "ã" to "a", "â" to "a", "ä" to "a",
            "é" to "e", "è" to "e", "ê" to "e", "ë" to "e",
            "í" to "i", "ì" to "i", "î" to "i", "ï" to "i",
            "ó" to "o", "ò" to "o", "õ" to "o", "ô" to "o", "ö" to "o",
            "ú" to "u", "ù" to "u", "û" to "u", "ü" to "u"
        )
        return letter.lowercase().map { map[it.toString()] ?: it }.joinToString("")
    }

    private fun handleCedilla(attemptLetter: String, wordLetter: String, word: String): String {
        val att = attemptLetter.lowercase()
        val wdL = wordLetter.lowercase()
        val wd = word.lowercase()

        if (att == "c" && wdL == "ç") return "ç"
        if (att == "c" && wd.contains("ç") && !wd.contains("c")) return "ç"

        return att
    }

    private fun normalizeWord(inputWord: String, targetWord: String): String {
        val inputChars = inputWord.split("").filter { it.isNotEmpty() } // Split seguro
        val targetChars = targetWord.split("").filter { it.isNotEmpty() }

        return inputChars.mapIndexed { index, letter ->
            val wordLetter = targetChars.getOrElse(index) { "" }
            val cedillaHandled = handleCedilla(letter, wordLetter, targetWord)
            normalizeLetter(cedillaHandled)
        }.joinToString("")
    }
}