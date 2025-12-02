package studio.styx.erisbot.discord.features.listeners

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.DiscordLocale
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.utils.messages.MessageEditData
import org.springframework.stereotype.Component
import shared.Cache
import shared.Colors
import studio.styx.erisbot.core.interfaces.CommandInterface
import studio.styx.erisbot.core.interfaces.ResponderInterface
import studio.styx.erisbot.generated.tables.records.CommandRecord
import studio.styx.schemaEXtended.core.exceptions.SchemaIllegalArgumentException
import utils.ComponentBuilder
import java.util.regex.Pattern

@Component
class InteractionListener(
    // Injeção automática de todos os comandos e responders
    private val commands: List<CommandInterface>,
    private val responders: List<ResponderInterface>
) : ListenerAdapter() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onCommandAutoCompleteInteraction(event: CommandAutoCompleteInteractionEvent) {
        scope.launch {
            val command = commands.find { it.getSlashCommandData().name == event.name }

            // 1. Se o comando não existe, retornamos lista vazia para parar o "loading" do Discord
            if (command == null) {
                event.replyChoices(emptyList()).queue()
                return@launch
            }

            try {
                // Executa a lógica do comando
                command.onAutoComplete(event)

            } catch (e: IllegalArgumentException) {
                System.err.println("Erro no AutoComplete do comando ${event.name}: ${e.message}")
                e.printStackTrace()
                handleAutoCompleteError(event, e, ErrorType.LIMIT_EXCEEDED)
            } catch (e: Exception) {
                System.err.println("Erro no AutoComplete do comando ${event.name}: ${e.message}")
                e.printStackTrace()
                handleAutoCompleteError(event, e, ErrorType.GENERIC)
            }
        }
    }

    // --- Helpers de Tratamento de Erro e Tradução ---

    private enum class ErrorType { LIMIT_EXCEEDED, GENERIC }

    private fun handleAutoCompleteError(event: CommandAutoCompleteInteractionEvent, e: Exception, type: ErrorType) {
        // Se a interação já foi respondida (ack), não podemos enviar outra resposta
        if (event.isAcknowledged) return

        val locale = event.userLocale

        // Seleciona a mensagem baseada no tipo de erro e idioma
        val errorMessage = when (type) {
            ErrorType.LIMIT_EXCEEDED -> getLocalizedMessage(
                locale,
                pt = "Erro: Mais de 25 opções retornadas!",
                en = "Error: More than 25 options returned!",
                es = "Error: ¡Más de 25 opciones devueltas!"
            )
            ErrorType.GENERIC -> getLocalizedMessage(
                locale,
                pt = "Erro ao carregar opções.",
                en = "Error loading options.",
                es = "Error al cargar opciones."
            )
        }

        // Cria uma Choice de erro. O value é "error_code" para você tratar se o usuário clicar sem querer.
        // O nome truncamos em 100 chars para evitar outro erro de validação.
        val errorChoice = Command.Choice("⚠️ $errorMessage".take(100), "error_interaction_failed")

        event.replyChoices(errorChoice).queue()
    }

    private fun getLocalizedMessage(locale: DiscordLocale, pt: String, en: String, es: String): String {
        return when (locale) {
            DiscordLocale.PORTUGUESE_BRAZILIAN -> pt
            DiscordLocale.SPANISH -> es
            else -> en // Default para Inglês
        }
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        // Lança uma corrotina para permitir o uso de 'suspend'
        scope.launch {
            handleSlashCommand(event)
        }
    }

    private suspend fun handleSlashCommand(event: SlashCommandInteractionEvent) {
        val command = commands.find { it.getSlashCommandData().name == event.name }

        if (command == null) {
            event.reply("Comando não encontrado.").setEphemeral(true).queue()
            return
        }

        try {
            // Lógica de Cache e verificação se está habilitado
            val cachedCommands: List<CommandRecord>? = Cache.get("commands")

            if (cachedCommands != null) {
                val commandName = event.name
                val subcommandGroup = event.subcommandGroup
                val subcommandName = event.subcommandName

                val cachedCommand = cachedCommands.firstOrNull { cmd ->
                    val nameMatch = commandName == cmd.name
                    val groupMatch = if (subcommandGroup != null) subcommandGroup == cmd.subcommandgroup else cmd.subcommandgroup == null
                    val subMatch = if (subcommandName != null) subcommandName == cmd.subcommand else cmd.subcommand == null

                    nameMatch && groupMatch && subMatch
                }

                if (cachedCommand?.isenabled == false) {
                    ComponentBuilder.ContainerBuilder.create()
                        .addText("Ops! esse comando foi desativado pelos meus desenvolvedores!")
                        .withColor(Colors.DANGER)
                        .reply(event)
                    return
                }
            }

            // Executa o comando
            command.execute(event)

        } catch (e: SchemaIllegalArgumentException) {
            val errorMessages = when {
                e.hasFieldErrors() -> e.fieldErrors.entries.joinToString("\n") { "- **`${it.key}: ${it.value}`**" }
                e.hasSimpleErrors() -> e.errors.joinToString("\n") { "- **`$it`**" }
                else -> "Erro de validação desconhecido"
            }

            ComponentBuilder.ContainerBuilder.create()
                .addText("## Informações inválidas!\n$errorMessages")
                .withColor(Colors.DANGER)
                .reply(event)

        } catch (e: Exception) {
            System.err.println("Erro ao executar comando ${event.name}: ${e.message}")
            e.printStackTrace()

            val container = ComponentBuilder.ContainerBuilder.create()
                .addText("Um erro ocorreu ao executar esse comando: **`${e.message}`**")
                .withColor(Colors.DANGER)
                .build()

            if (event.isAcknowledged) {
                event.hook.editOriginalComponents(container).useComponentsV2().queue();
            } else {
                // Se não, respondemos normalmente
                event.replyComponents(container).useComponentsV2().queue();
            }

        }
    }

    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        scope.launch { handleInteraction(event, event.componentId) }
    }

    override fun onStringSelectInteraction(event: StringSelectInteractionEvent) {
        scope.launch { handleInteraction(event, event.componentId) }
    }

    override fun onModalInteraction(event: ModalInteractionEvent) {
        scope.launch { handleInteraction(event, event.modalId) }
    }

    override fun onEntitySelectInteraction(event: EntitySelectInteractionEvent) {
        scope.launch { handleInteraction(event, event.componentId) }
    }

    private suspend fun handleInteraction(event: Any, componentId: String) {
        for (responder in responders) {
            val customIdPattern = responder.customId
            val regex = customIdPattern.replace(":(\\w+)".toRegex(), "[^/]+")
            val pattern = Pattern.compile("^$regex$")
            val matcher = pattern.matcher(componentId)

            if (matcher.matches()) {
                try {
                    // O Kotlin escolhe automaticamente qual 'execute' chamar baseado no tipo do evento
                    when (event) {
                        is ButtonInteractionEvent -> responder.execute(event)
                        is StringSelectInteractionEvent -> responder.execute(event)
                        is ModalInteractionEvent -> responder.execute(event)
                        is EntitySelectInteractionEvent -> responder.execute(event)
                        else -> replyError(event, "Tipo de evento não suportado.")
                    }
                } catch (e: Exception) {
                    System.err.println("Erro na interação $componentId: ${e.message}")
                    e.printStackTrace()
                    replyError(event, "Erro ao processar interação.")
                }
                return
            }
        }
        replyError(event, "Interação não encontrada.")
    }

    private fun replyError(event: Any, message: String) {
        val replyAction = when (event) {
            is ButtonInteractionEvent -> event.reply(message)
            is StringSelectInteractionEvent -> event.reply(message)
            is ModalInteractionEvent -> event.reply(message)
            is EntitySelectInteractionEvent -> event.reply(message)
            else -> null
        }
        replyAction?.setEphemeral(true)?.queue()
    }
}