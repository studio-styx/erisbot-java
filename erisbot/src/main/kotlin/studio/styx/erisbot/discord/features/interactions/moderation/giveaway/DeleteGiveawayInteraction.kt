package studio.styx.erisbot.discord.features.interactions.moderation.giveaway

import dev.minn.jda.ktx.coroutines.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import shared.Colors
import shared.utils.CustomIdHelper
import shared.utils.Icon
import shared.utils.MentionUtil.channelMention
import studio.styx.erisbot.core.exceptions.InteractionUsedByUnauthorizedUserException
import studio.styx.erisbot.core.extensions.jda.reply.rapidContainerEdit
import studio.styx.erisbot.core.extensions.jda.reply.rapidContainerReply
import studio.styx.erisbot.core.interfaces.ResponderInterface
import studio.styx.erisbot.discord.menus.giveaway.giveawayMenu
import studio.styx.erisbot.generated.tables.records.GiveawayRecord
import studio.styx.erisbot.generated.tables.records.GuildgiveawayRecord
import studio.styx.erisbot.generated.tables.references.GIVEAWAY
import studio.styx.erisbot.generated.tables.references.GUILDGIVEAWAY
import utils.ComponentBuilder

@Component
class DeleteGiveawayInteraction : ResponderInterface {
    override val customId = "giveaway/delete/:action/:giveawayId/:userId"

    @Autowired
    lateinit var dsl: DSLContext

    override suspend fun execute(event: ButtonInteractionEvent) {
        val params = CustomIdHelper(customId, event.customId)

        val action = params.get("action")!!
        val giveawayId = params.getAsInt("giveawayId")!!
        val userId = params.get("userId")!!

        if (userId != event.user.id) throw InteractionUsedByUnauthorizedUserException(userId)

        if (action == "confirm") {
            event.deferEdit()

            val (giveawayRecord, connectedGuilds) = coroutineScope {
                val giveawayDeferred = async(Dispatchers.IO) {
                    dsl.selectFrom(GIVEAWAY)
                        .where(GIVEAWAY.ID.eq(giveawayId))
                        .fetchOne()
                }

                val connectedGuildsDeferred = async(Dispatchers.IO) {
                    dsl.selectFrom(GUILDGIVEAWAY)
                        .where(GUILDGIVEAWAY.GIVEAWAYID.eq(giveawayId))
                        .fetch()
                }

                Pair(
                    giveawayDeferred.await(),
                    connectedGuildsDeferred.await()
                )
            }

            if (giveawayRecord == null) {
                event.rapidContainerReply(Colors.DANGER, "${Icon.static.get("denied")} | Sorteio não encontrado!")
                return
            }

            if (giveawayRecord.ended == true) {
                event.rapidContainerReply(Colors.DANGER, "${Icon.static.get("denied")} | Este sorteio já foi encerrado/cancelado!")
                return
            }

            val guildConnected = connectedGuilds.find { it.guildid == event.guild!!.id }
            if (guildConnected == null) {
                event.rapidContainerReply(Colors.DANGER, "${Icon.static.get("denied")} | Este sorteio não está conectado a esse server!")
                return
            }

            val guildIsHost = guildConnected.ishost!!

            if (guildIsHost) {
                val errors = handleServerHostDelete(event, giveawayRecord, connectedGuilds, event.user.effectiveName)

                val isSuccess = errors.isEmpty()

                val message = if (isSuccess) {
                    "${Icon.static.get("success")} | O sorteio foi excluido com sucesso!"
                } else {
                    val errorMessage = errors.joinToString("\n") { e -> "${e.guildName ?: e.guildId} - ${e.error}" }
                    "${Icon.static.get("error")} | O sorteio foi excluido mas com alguns erros: \n $errorMessage"
                }

                event.rapidContainerReply(
                    if (isSuccess) Colors.SUCCESS else Colors.DANGER,
                    message
                )
            } else {
                handleRemoveServerFromGiveaway(event, giveawayRecord, guildConnected)
                event.rapidContainerReply(
                    Colors.SUCCESS,
                    "${Icon.static.get("success")} | O server foi retirado do sorteio com sucesso!"
                )
            }
        } else {
            event.rapidContainerEdit(
                Colors.SUCCESS,
                "${Icon.static.get("success")} | Você cancelou a ação!"
            )
        }
    }

    private data class GiveawayDeleteError(
            val guildId: String,
            val guildName: String?,
            val error: String
    )

    private suspend fun handleRemoveServerFromGiveaway(
        event: ButtonInteractionEvent,
        giveaway: GiveawayRecord,
        connectedGuild: GuildgiveawayRecord,
    ) {
        dsl.deleteFrom(GUILDGIVEAWAY)
            .where(GUILDGIVEAWAY.GUILDID.eq(event.guild!!.id))
            .and(GUILDGIVEAWAY.GIVEAWAYID.eq(giveaway.id!!))
            .execute()

        val channel = event.guild!!.getTextChannelById(connectedGuild.channelid!!) ?: return
        val message = runCatching {
            channel.retrieveMessageById(connectedGuild.messageid!!).await()
        }.getOrNull()

        val content = "${Icon.static.get("Eris_cry")} | O moderador ${event.user.asMention} removeu o server do sorteio ${giveaway.title!!}"

        val container = ComponentBuilder.ContainerBuilder.create()
            .withColor(Colors.DANGER)
            .addText(content)
            .build()

        try {
            val menu = giveawayMenu(
                giveaway,
                emptyList(),
                emptyList(),
                0,
                event.guild!!.id,
                true
            )
            message?.editMessageComponents(menu)?.useComponentsV2()?.await()
            message?.replyComponents(container)?.useComponentsV2()?.await() ?: run {
                channel.sendMessageComponents(container).useComponentsV2().await()
            }
        } catch (_: Exception) { }
    }

    private suspend fun handleServerHostDelete(
        event: ButtonInteractionEvent,
        giveaway: GiveawayRecord,
        connectedGuilds: List<GuildgiveawayRecord>,
        guildHostName: String
    ): MutableList<GiveawayDeleteError> {
        val errors: MutableList<GiveawayDeleteError> = mutableListOf()

        dsl.deleteFrom(GIVEAWAY)
            .where(GIVEAWAY.ID.eq(giveaway.id!!))
            .execute()

        coroutineScope {
            connectedGuilds.forEach { cg ->
                async {
                    val guild = event.jda.getGuildById(cg.guildid!!) ?: run {
                        errors.add(GiveawayDeleteError(
                            guildId = cg.guildid!!,
                            guildName = null,
                            error = "Server não encontrado"
                        ))
                        return@async
                    }
                    val channel = guild.getTextChannelById(cg.channelid!!) ?: run {
                        errors.add(GiveawayDeleteError(
                            guildId = cg.guildid!!,
                            guildName = guild.name,
                            error = "Canal ${channelMention(cg.channelid!!)} id: `${cg.channelid!!}` não encontrado"
                        ))
                        return@async
                    }
                    val message = runCatching {
                        channel.retrieveMessageById(cg.messageid!!).await()
                    }.getOrNull() ?: run {
                        errors.add(GiveawayDeleteError(
                            guildId = cg.guildid!!,
                            guildName = guild.name,
                            error = "Não foi possivel encontrar a mensagem do sorteio"
                        ))
                        return@async
                    }

                    try {
                        val sanitizedName = event.user.effectiveName.replace(Regex("([\\\\_*~`|>])"), "\\\\$1")

                        val container = ComponentBuilder.ContainerBuilder.create()
                            .withColor(Colors.DANGER)
                            .addText("Esse sorteio foi deletado pelo moderador: **$sanitizedName**")
                            .addText("Do server host: $guildHostName")

                        message.editMessageComponents(
                            container.build()
                        ).await()
                    } catch (_: Exception) {
                        errors.add(GiveawayDeleteError(
                            guildId = cg.guildid!!,
                            guildName = guild.name,
                            error = "Não foi possivel editar a mensagem do sorteio"
                        ))
                    }
                }
            }
        }

        return errors
    }
}