package studio.styx.erisbot.discord.features.listeners

import dev.minn.jda.ktx.coroutines.await
import functions.KtorClientManager
import functions.football.ApiFootballDataSdk
import functions.football.register.RegisterMatches
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import shared.Colors
import shared.utils.DiscordTimeStyle
import shared.utils.Utils
import studio.styx.erisbot.discord.features.events.message.trivia.WriteResponse
import utils.ComponentBuilder

@Component
class MessageListener : ListenerAdapter() {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Autowired
    private lateinit var dsl: DSLContext

    override fun onMessageReceived(event: MessageReceivedEvent) {
        if (event.author.id == "1171963692984844401") {
            val prefix = "e."
            if (event.message.contentRaw.startsWith(prefix)) {
                val args = event.message.contentRaw.substring(prefix.length).trim().split("\\s+".toRegex())
                val command = args[0].lowercase()
                // val params = args.subList(1, args.size)
                scope.launch {
                    when (command) {
                        "fixturegames" -> {
                            val register = RegisterMatches(
                                sdk = ApiFootballDataSdk(
                                    KtorClientManager.getClient()
                                ),
                                dsl = dsl,
                                jda = event.jda
                            )

                            val msg = event.message.replyComponents(
                                ComponentBuilder.ContainerBuilder.create()
                                    .addText("## Iniciando registro de partidas")
                                    .withColor(Colors.WARNING)
                                    .build()
                            ).useComponentsV2().await()

                            try {
                                val result = register.registerFootballMatches()
                                msg.editMessageComponents(
                                    ComponentBuilder.ContainerBuilder.create()
                                        .addText("## Registro concluído com sucesso")
                                        .addDivider()
                                        .addText(Utils.brBuilder(
                                            "### Resultado:",
                                            "**Sucesso**: ${result.success.size}",
                                            "**Falhou**: ${result.failed.size}",
                                            "**Erros**: ${result.errors.size}",
                                            "**Duração**: ${result.durationMinutes} minutos",
                                            "**Iniciado em**: ${Utils.formatDiscordTime(result.startedAt, DiscordTimeStyle.SHORTTIME)}",
                                            "**Finalizado em**: ${Utils.formatDiscordTime(result.finishedAt, DiscordTimeStyle.SHORTTIME)}",
                                        ))
                                        .withColor(Colors.SUCCESS)
                                        .build()
                                ).useComponentsV2().await()
                            } catch (e: Exception) {
                                e.printStackTrace()
                                msg.editMessageComponents(
                                    ComponentBuilder.ContainerBuilder.create()
                                        .addText("## Registro falhou")
                                        .addDivider()
                                        .addText("Erro: ${e.message}")
                                        .withColor(Colors.DANGER)
                                        .build()
                                ).useComponentsV2().await()
                            }
                        }
                    }
                }
            }
        }

        scope.launch {
            WriteResponse().execute(event) // resposta de texto trivia
        }
    }
}