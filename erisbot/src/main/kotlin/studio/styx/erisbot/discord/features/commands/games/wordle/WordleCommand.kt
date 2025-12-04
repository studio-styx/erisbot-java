package studio.styx.erisbot.discord.features.commands.games.wordle

import dev.minn.jda.ktx.coroutines.await
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.DiscordLocale
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.utils.FileUpload
import org.springframework.stereotype.Component
import shared.Cache
import shared.Colors
import shared.utils.Icon
import shared.utils.Utils
import studio.styx.erisbot.core.dtos.wordle.WordleGameDto
import studio.styx.erisbot.core.extensions.jda.reply.rapidContainerReply
import studio.styx.erisbot.core.interfaces.CommandInterface
import studio.styx.erisbot.functions.helpers.wordle.GetWordsHelper
import studio.styx.erisbot.functions.imageGenerator.wordle.WordleImageGenerator
import utils.ComponentBuilder

@Component
class WordleCommand : CommandInterface {
    override fun getSlashCommandData(): SlashCommandData {
        return Commands.slash("wordle", "plays a game of wordle")
            .addOptions(
                OptionData(OptionType.INTEGER, "length", "the length of the word")
                    .setMinValue(4)
                    .setMaxValue(6),
                OptionData(OptionType.STRING, "language", "the language of the word (default: you language")
                    .addChoices(
                        Command.Choice("Portuguese", "portuguese"),
                        Command.Choice("English", "english"),
                        Command.Choice("Spanish", "spanish"),
                    )
            )
    }

    override suspend fun execute(event: SlashCommandInteractionEvent) {
        val game = Cache.get<WordleGameDto>("wordle:${event.user.id}")

        if (game != null) {
            val linkToMessage = "https://discord.com/channels/${game.guildId}/${game.channelId}/${game.messageId}"

            ComponentBuilder.ContainerBuilder.create()
                .withColor(Colors.DANGER)
                .addText("${Icon.static.get("denied")} | Você já está em uma partida de termo! Continue jogando **[aqui]($linkToMessage)**")
                .addRow(ActionRow.of(
                    Button.danger("wordle/deleteGame/${event.user.id}", "Deletar Jogo")
                ))
                .reply(event)
            return
        }

        val length = event.getOption("length")?.asInt ?: Utils.getRandomInt(4, 6)
        val selectedLanguage = event.getOption("language")?.asString

        val language = when(selectedLanguage) {
            "portuguese" -> DiscordLocale.PORTUGUESE_BRAZILIAN
            "spanish" -> DiscordLocale.SPANISH
            "english" -> DiscordLocale.ENGLISH_US
            else -> when(event.userLocale) {
                DiscordLocale.PORTUGUESE_BRAZILIAN -> DiscordLocale.PORTUGUESE_BRAZILIAN
                DiscordLocale.SPANISH, DiscordLocale.SPANISH_LATAM -> DiscordLocale.SPANISH
                else -> DiscordLocale.ENGLISH_US
            }
        }

        val words = GetWordsHelper(language, length).getWords()

        val selectedWord = words.random()

        try {
            val imageBytes = WordleImageGenerator.createWordleImage(selectedWord.word, emptyList())
            val message = event.replyFiles(FileUpload.fromData(imageBytes, "termo.png"))
                .addComponents(ActionRow.of(
                    Button.primary("wordle/writeWord/${event.user.id}", "Escrever")
                )).await()

            Cache.set("wordle:${event.user.id}", WordleGameDto(
                word = selectedWord.word,
                guildId = event.guild!!.id,
                channelId = event.channel.id,
                messageId = message.id,
                userId = event.user.id,
                attempts = mutableListOf(),
                isOver = false,
                isWon = false
            ), 1800)
        } catch (e: Exception) {
            event.rapidContainerReply(Colors.DANGER, "Erro: ${e.message}", true)
        }
    }
}