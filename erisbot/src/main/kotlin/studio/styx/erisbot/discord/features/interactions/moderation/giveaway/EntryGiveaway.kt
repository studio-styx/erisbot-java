package studio.styx.erisbot.discord.features.interactions.moderation.giveaway

import database.extensions.giveaways
import dev.minn.jda.ktx.coroutines.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import shared.Colors
import shared.utils.CustomIdHelper
import shared.utils.Icon
import discord.extensions.jda.reply.rapidContainerReply
import studio.styx.erisbot.core.interfaces.ResponderInterface
import menus.giveaway.GiveawayMenuConnectedGuildExpectedValues
import menus.giveaway.giveawayMenu
import functions.giveaway.VerifyUserRequirement
import functions.giveaway.getGiveawayRoleEntriesFormatted

@Component
class EntryGiveaway : ResponderInterface {
    @Autowired
    lateinit var dsl: DSLContext

    override val customId = "giveaway/entry/:giveawayId"

    override suspend fun execute(event: ButtonInteractionEvent) {
        val params = CustomIdHelper(customId, event.customId)

        val giveawayId = params.getAsInt("giveawayId")!!

        event.deferReply(true).await()

        val (giveaway, participants, connectedGuilds, roleEntries) = dsl.giveaways.getGiveawayWithParticipantsCount(giveawayId) ?: run {
            event.rapidContainerReply(
                Colors.DANGER,
                "${Icon.static.get("error")} | Eu procurei por toda parte mas não consegui encontrar esse sorteio!",
                true
            )
            return
        }

        val guildConnected = connectedGuilds.find { it.guildid == event.guild!!.id } ?: run {
            event.rapidContainerReply(
                Colors.DANGER,
                "${Icon.static.get("denied")} | Esse sorteio não está conectado a esse server!",
                true
            )
            return
        }

        if (giveaway.ended == true) {
            event.rapidContainerReply(
                Colors.DANGER,
                "${Icon.static.get("denied")} | Sindo muito mas esse sorteio já foi finalizado!",
                true
            )
            return
        }

        val connectedGuildsFormatted = connectedGuilds.mapNotNull { cnn ->
            val guild = event.jda.getGuildById(cnn.guildid!!) ?: return@mapNotNull null
            GiveawayMenuConnectedGuildExpectedValues(guild.name, cnn)
        }

        val (roleEntriesFormatted, messages, requirementsVerifyResult) = coroutineScope {
            val roleEntriesFormattedDeferred = async(Dispatchers.Default) {
                getGiveawayRoleEntriesFormatted(roleEntries, connectedGuilds, event.jda)
            }

            val messagesDeferred = async(Dispatchers.IO) {
                connectedGuilds.map { cnn ->
                    async {
                        runCatching {
                            val guild = event.jda.getGuildById(cnn.guildid!!) ?: return@runCatching null
                            val channel = guild.getTextChannelById(cnn.channelid!!) ?: return@runCatching null
                            val messageId = cnn.messageid ?: return@runCatching null
                            channel.retrieveMessageById(messageId).await()
                        }.getOrNull() // Retorna null se falhar ao buscar a mensagem (ex: deletada)
                    }
                }.awaitAll().filterNotNull()
            }

            val requirementsVerifyResultDeferred = async(Dispatchers.IO) {
                VerifyUserRequirement(
                    event.jda,
                    giveaway,
                    event.user.id,
                    connectedGuilds,
                    dsl
                ).verify()
            }

            Triple(
                roleEntriesFormattedDeferred.await(),
                messagesDeferred.await(),
                requirementsVerifyResultDeferred.await()
            )
        }

        if (requirementsVerifyResult.errors.isNotEmpty()) {
            event.rapidContainerReply(
                Colors.DANGER,
                "${Icon.static.get("denied")} | ${requirementsVerifyResult.errors.joinToString("\n")}",
                true
            )
        }

        val menu = giveawayMenu(
            giveaway,
            roleEntriesFormatted,
            connectedGuildsFormatted,
            participants,
            event.guild!!.id,
            true
        )

        event.rapidContainerReply(
            Colors.SUCCESS,
            "${Icon.static.get("success")} | Você entrou no sorteio com sucesso!",
            true
        )

        coroutineScope {
            messages.map { m ->
                launch {
                    try {
                        m.editMessageComponents(menu).useComponentsV2().await()
                    } catch (_: Exception) {
                        // Ignora falhas de UI (ex: permissão removida)
                    }
                }
            }
            // O coroutineScope aguardará automaticamente todos os launches filhos terminarem
        }
    }
}