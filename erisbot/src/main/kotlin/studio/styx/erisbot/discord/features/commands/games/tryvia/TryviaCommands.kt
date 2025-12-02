package studio.styx.erisbot.discord.features.commands.games.tryvia

import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.interactions.commands.Subcommand
import games.tryvia.core.TryviaGame
import games.tryvia.dtos.TryviaParticipant
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import shared.Cache
import shared.Colors
import shared.utils.Icon
import shared.utils.MentionUtil
import studio.styx.erisbot.core.interfaces.CommandInterface
import studio.styx.erisbot.discord.menus.tryviaEndGameMenu
import studio.styx.erisbot.generated.enums.Tryviadifficulty
import studio.styx.erisbot.generated.enums.Tryviatypes
import studio.styx.erisbot.generated.tables.records.TryviaquestionsRecord
import studio.styx.erisbot.generated.tables.references.GUILDMEMBER
import studio.styx.erisbot.generated.tables.references.GUILDSETTINGS
import studio.styx.erisbot.generated.tables.references.TRYVIAQUESTIONS
import studio.styx.erisbot.generated.tables.references.USER
import utils.ComponentBuilder
import java.math.BigDecimal
import java.time.LocalDateTime

@Component
class TryviaCommands : CommandInterface {
    private val MIN_PARTICIPANTS_FOR_STATS = 2
    private val MIN_POINTS_FOR_STATS = 5
    private val MIN_GAME_DURATION = 60 // segundos
    private val MIN_POINTS_THRESHOLD = 10
    private val difficultyMap = mapOf(
        "easy" to listOf(Tryviadifficulty.EASY),
        "medium" to listOf(Tryviadifficulty.MEDIUM),
        "hard" to listOf(Tryviadifficulty.HARD),
        "easy_medium" to listOf(Tryviadifficulty.EASY, Tryviadifficulty.MEDIUM),
        "easy_hard" to listOf(Tryviadifficulty.EASY, Tryviadifficulty.HARD),
        "medium_hard" to listOf(Tryviadifficulty.MEDIUM, Tryviadifficulty.HARD),
    )
    private val typeMap = mapOf(
        "boolean" to listOf(Tryviatypes.BOOLEAN),
        "multiple" to listOf(Tryviatypes.MULTIPLE),
        "writeInChat" to listOf(Tryviatypes.WRITEINCHAT),
        "multiple_boolean" to listOf(Tryviatypes.MULTIPLE, Tryviatypes.BOOLEAN),
        "multiple_writeInChat" to listOf(Tryviatypes.MULTIPLE, Tryviatypes.WRITEINCHAT),
        "boolean_writeInChat" to listOf(Tryviatypes.BOOLEAN, Tryviatypes.WRITEINCHAT)
    )

    @Autowired
    lateinit var dsl: DSLContext

    override fun getSlashCommandData(): SlashCommandData {
        val tryviaStartOptions = mutableListOf(
            OptionData(OptionType.STRING, "category", "category of the questions")
                .setAutoComplete(true),
            OptionData(OptionType.INTEGER, "amount", "amount of questions"),
            OptionData(OptionType.STRING, "difficulty", "difficulty of the questions")
                .addChoices(
                    Command.Choice("easy", "easy"),
                    Command.Choice("medium", "medium"),
                    Command.Choice("hard", "hard"),
                    Command.Choice("easy_medium", "easy_medium"),
                    Command.Choice("easy_hard", "easy_hard"),
                    Command.Choice("medium_hard", "medium_hard")
                ),
            OptionData(OptionType.STRING, "type", "type of the questions")
                .addChoices(
                    Command.Choice("multiple", "multiple"),
                    Command.Choice("boolean", "boolean"),
                    Command.Choice("writeInChat", "writeInChat"),
                    Command.Choice("multiple_boolean", "multiple_boolean"),
                    Command.Choice("multiple_writeInChat", "multiple_writeInChat"),
                    Command.Choice("boolean_writeInChat", "boolean_writeInChat")
                )
        )

        val tryviaStart = Subcommand("start", "inicia um jogo de trivia")
            .addOptions(tryviaStartOptions)
        val tryviaClose = Subcommand("close", "fecha um jogo de trivia acontecendo no mesmo canal")

        return Commands.slash("tryvia", "comandos de trivia")
            .addSubcommands(tryviaStart, tryviaClose)
    }

    override suspend fun onAutoComplete(event: CommandAutoCompleteInteractionEvent) {
        // Verifica se estamos no subcomando "start" e na opção correta
        if (event.subcommandName == "start" && event.focusedOption.name == "category") {
            val input = event.focusedOption.value

            if (input.isBlank()) {
                event.replyChoices(
                    Command.Choice("Por favor escreva uma categoria", "none")
                ).queue()
                return
            }

            val categories = getCategories()

            val filtered = categories.filter {
                it.contains(input, ignoreCase = true)
            }

            if (filtered.isEmpty()) {
                event.replyChoices(
                    Command.Choice("Nenhuma categoria encontrada", "none")
                ).queue()
                return
            }

            val choices = filtered.take(25).map { category ->
                Command.Choice(category, category)
            }

            event.replyChoices(choices).queue()
        }
    }

    private suspend fun getCategories(): List<String> {
        val cacheKey = "tryvia:questions:categories"

        val cached: List<String>? = Cache.get(cacheKey)

        if (cached != null) {
            return cached
        }

        // Se não tem no cache, busca no banco via jOOQ
        val records = dsl.select(TRYVIAQUESTIONS.TAGS)
            .from(TRYVIAQUESTIONS)
            .fetch()

        val tags = records
            .mapNotNull { it.value1() } // Pega o valor da coluna TAGS, ignora nulos
            .flatMap { it.toList() }     // Converte Array Java para Lista e "Achata"
            .toSet()
            .toList()

        // Salva no Cache (30 minutos = 1800 segundos)
        Cache.set(cacheKey, tags, 1800)

        return tags.filterNotNull()
    }

    override suspend fun execute(event: SlashCommandInteractionEvent) {
        val subCommand = event.subcommandName

        when (subCommand) {
            "start" -> tryviaStart(event)
            "close" -> tryviaClose(event)
        }
    }

    private suspend fun tryviaStart(event: SlashCommandInteractionEvent) {
        if (event.channel == null) {
            ComponentBuilder.ContainerBuilder.create()
                .addText("${Icon.static.get("denied")} | Para iniciar um jogo de trivia é necessário iniciar o jogo em um canal válido")
                .withColor(Colors.DANGER)
                .reply(event)
            return
        }

        event.deferReply().await()
        val key = "tryvia:game:${event.channel.id}"
        val game = Cache.get<TryviaGame>(key)

        if (game != null) {
            ComponentBuilder.ContainerBuilder.create()
                .addText("${Icon.static.get("denied")} | Já existe um jogo de trivia nesse canal! para finalizar use **`/trivia fechar`**")
                .withColor(Colors.DANGER)
                .reply(event)
            return
        }

        val categoryInput = event.getOption("category")?.asString
        val amountInput = event.getOption("amount")?.asInt ?: 10
        val difficultyInput = event.getOption("difficulty")?.asString
        val typeInput = event.getOption("type")?.asString

        val questionsDifficulty = difficultyMap[difficultyInput] ?: listOf(Tryviadifficulty.EASY, Tryviadifficulty.MEDIUM, Tryviadifficulty.HARD)
        val questionsType = typeMap[typeInput] ?: listOf(Tryviatypes.MULTIPLE, Tryviatypes.BOOLEAN, Tryviatypes.WRITEINCHAT)

        val questions = getTryviaQuestions(categoryInput, amountInput, questionsDifficulty, questionsType).shuffled()

        if (questions.isEmpty()) {
            event.hook.editOriginalComponents(
                ComponentBuilder.ContainerBuilder.create()
                    .addText("${Icon.static.get("error")} | Nenhum jogo foi encontrado nos critérios atuais")
                    .withColor(Colors.DANGER)
                    .build()
            )
            return
        }

        val gameInstance = TryviaGame(
            event.user.id,
            questions.toMutableList(),
            mutableListOf(TryviaParticipant(
                event.user.id
            )),
            dsl,
            0,
        )

        // fazer upsert em tudo
        dsl.transaction { config ->
            run {
                val tx = config.dsl()

                tx.insertInto(USER)
                    .columns(USER.ID, USER.MONEY, USER.CREATEDAT, USER.UPDATEDAT)
                    .values(event.user.id, BigDecimal.ZERO, LocalDateTime.now(), LocalDateTime.now())
                    .onConflictDoNothing()
                    .execute()
                tx.insertInto(GUILDSETTINGS)
                    .set(GUILDSETTINGS.ID, event.guild!!.id)
                    .onDuplicateKeyIgnore()
                    .execute()
                tx.insertInto(GUILDMEMBER)
                    .set(GUILDMEMBER.ID, event.user.id)
                    .set(GUILDMEMBER.GUILDID, event.guild!!.id)
                    .onConflictDoNothing()
                    .execute()
            }
        }

        Cache.set(key, gameInstance)

        event.hook.editOriginalComponents(
            ComponentBuilder.ContainerBuilder.create()
                .addText("${Icon.static.get("Eris_happy")} | Um novo jogo de trivia foi iniciado nesse canal!${if(questions.size < amountInput) " Infelizmente não consegui encontrar todas as $amountInput perguntas requiridas, apenas ${questions.size}" else ""}")
                .withColor(Colors.SUCCESS)
                .build()
        ).useComponentsV2(true).await()

        val channel = event.channel

        gameInstance.sendQuestionMessage(channel)
    }

    private fun getTryviaQuestions(category: String?, amount: Int, difficulty: List<Tryviadifficulty>, type: List<Tryviatypes>): List<TryviaquestionsRecord> {
        val conditions = mutableListOf<org.jooq.Condition>(
            TRYVIAQUESTIONS.DIFFICULTY.`in`(difficulty),
            TRYVIAQUESTIONS.TYPE.`in`(type)
        )

        if (category != null) {
            conditions.add(TRYVIAQUESTIONS.TAGS.contains(arrayOf(category)))
        }

        return dsl.selectFrom(TRYVIAQUESTIONS)
            .where(conditions)
            .orderBy(DSL.rand())
            .limit(amount)
            .fetch()
    }

    private suspend fun tryviaClose(event: SlashCommandInteractionEvent) {
        // Validar canal primeiro
        val channel = event.channel
        if (!channel.canTalk()) {
            sendError(event, "Não posso enviar mensagens neste canal!")
            return
        }

        val key = "tryvia:game:${channel.id}"
        val game = Cache.get<TryviaGame>(key)

        if (game == null) {
            sendError(event, "${Icon.static.get("denied")} | Nenhum jogo ativo neste canal!")
            return
        }

        val member = event.member ?: run {
            sendError(event, "${Icon.static.get("denied")} | Comando requer servidor!")
            return
        }

        // Validar se o jogo não está vazio
        if (game.getParticipants().isEmpty()) {
            Cache.remove(key)
            sendError(event, "${Icon.static.get("info")} | Jogo sem participantes foi removido!")
            return
        }

        if (!canCloseTryviaGame(game, member)) {
            sendError(event,
                "${Icon.static.get("denied")} | Você não tem permissão para encerrar este jogo!\n" +
                        "Apenas o dono (${MentionUtil.userMention(game.getOwnerId())}) ou moderadores."
            )
            return
        }

        Cache.remove(key)

        // Responder ao usuário imediatamente
        event.deferReply(true).await()
        event.hook.editOriginalComponents(
            ComponentBuilder.ContainerBuilder.create()
                .addText("${Icon.static.get("success")} | Encerrando jogo...")
                .withColor(Colors.SUCCESS)
                .build()
        ).useComponentsV2(true).await()

        try {
            game.handleTryviaEnd(channel)
            sendSuccess(event, "${Icon.static.get("success")} | Jogo encerrado com sucesso!")
        } catch (e: Exception) {
            println("Erro ao processar fechamento do jogo:, $e")
        }
    }


    private fun canCloseTryviaGame(game: TryviaGame, member: Member): Boolean {
        // Dono do jogo pode sempre fechar
        if (game.getOwnerId() == member.id) return true

        // Moderadores/Administradores
        return member.hasPermission(
            Permission.MANAGE_CHANNEL,
            Permission.MANAGE_SERVER,
            Permission.ADMINISTRATOR,
            Permission.MANAGE_THREADS
        )
    }

    private fun sendError(event: SlashCommandInteractionEvent, message: String) {
        ComponentBuilder.ContainerBuilder.create()
            .addText(message)
            .setEphemeral(true)
            .withColor(Colors.DANGER)
            .reply(event)
    }

    private suspend fun sendSuccess(event: SlashCommandInteractionEvent, message: String) {
        event.hook.editOriginalComponents(
            ComponentBuilder.ContainerBuilder.create()
                .addText(message)
                .withColor(Colors.SUCCESS)
                .build()
        ).useComponentsV2(true).await()
    }
}