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
import studio.styx.erisbot.core.extensions.jda.reply.rapidContainerReply
import studio.styx.erisbot.core.interfaces.ResponderInterface
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
                handleServerHostDelete(event, giveawayRecord, connectedGuilds, event.user.effectiveName)
            } else {

            }
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
        connectedGuilds: List<GuildgiveawayRecord>,
    ) {

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
                    val message = try {
                        channel.retrieveMessageById(cg.messageid!!).await()
                    } catch (_: Exception) {
                        null
                    } ?: run {
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