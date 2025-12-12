package studio.styx.erisbot.discord.features.commands.moderation.giveaway.subCommands

import database.extensions.giveaways
import dev.minn.jda.ktx.coroutines.await
import kotlinx.coroutines.*
import net.dv8tion.jda.api.components.container.Container
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import org.jooq.DSLContext
import shared.Colors
import shared.utils.Icon
import shared.utils.MentionUtil.userMention
import discord.extensions.jda.reply.rapidContainerReply
import menus.giveaway.GiveawayMenuConnectedGuildExpectedValues
import menus.giveaway.giveawayMenu
import functions.giveaway.GiveawaySelectWinner
import functions.giveaway.getGiveawayRoleEntriesFormatted
import studio.styx.erisbot.generated.tables.records.*
import studio.styx.erisbot.generated.tables.references.*
import studio.styx.schemaEXtended.core.schemas.NumberSchema
import utils.ComponentBuilder
import java.time.LocalDateTime

private data class GiveawayDataE(
    val giveaway: GiveawayRecord?,
    val participants: List<UsergiveawayRecord>,
    val connectedGuilds: List<GuildgiveawayRecord>,
    val roleEntries: List<RolemultipleentryRecord>
)

private val GIVEAWAY_END_SCHEMA = NumberSchema()
    .parseError("Id de sorteio inválido")
    .min(1)
    .minError("Id de sorteio muito baixo")
    .coerce()

suspend fun endGiveawayCommand(event: SlashCommandInteractionEvent, dsl: DSLContext) {
    val giveawayId = GIVEAWAY_END_SCHEMA.parseOrThrow(event.getOption("id")?.asString).toInt()

    event.deferReply().await()

    // BUSCA DE DADOS
    val data = dsl.giveaways.getGiveawayFull(giveawayId)

    if (data == null) {
        event.rapidContainerReply(Colors.DANGER, "${Icon.static.get("error")} | Não consegui encontrar esse sorteio!")
        return
    }

    val (giveaway, participants, connectedGuilds, roleEntries) = data

    if (giveaway.ended == true) {
        event.rapidContainerReply(Colors.DANGER, "${Icon.static.get("error")} | Esse sorteio já terminou!")
        return
    }

    // PROCESSAMENTO LÓGICO (Vencedores e Formatadores)
    val (winners, roleEntriesFormatted, messages) = coroutineScope {
        val winnersDeferred = async(Dispatchers.Default) {
            GiveawaySelectWinner(
                event.jda, dsl, giveaway, participants, connectedGuilds, roleEntries, giveaway.userswins ?: 1
            ).select()
        }

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

        Triple(winnersDeferred.await(), roleEntriesFormattedDeferred.await(), messagesDeferred.await())
    }

    // UPDATE NO BANCO
    withContext(Dispatchers.IO) {
        dsl.transaction { configuration ->
            val transDsl = configuration.dsl()

            transDsl.update(GIVEAWAY)
                .set(GIVEAWAY.ENDED, true)
                .set(GIVEAWAY.EXPIRESAT, LocalDateTime.now())
                .set(GIVEAWAY.UPDATEDAT, LocalDateTime.now())
                .where(GIVEAWAY.ID.eq(giveawayId))
                .execute()

            if (!winners.isNullOrEmpty()) {
                transDsl.update(USERGIVEAWAY)
                    .set(USERGIVEAWAY.ISWINNER, true)
                    .where(USERGIVEAWAY.GIVEAWAYID.eq(giveawayId))
                    .and(USERGIVEAWAY.USERID.`in`(winners.map { it.userid }))
                    .execute()
            }
        }
    }

    // CONSTRUÇÃO UI
    val connectedGuildsFormatted = connectedGuilds.mapNotNull { cnn ->
        val guild = event.jda.getGuildById(cnn.guildid!!) ?: return@mapNotNull null
        GiveawayMenuConnectedGuildExpectedValues(guild.name, cnn)
    }

    val menu = giveawayMenu(
        giveaway,
        roleEntriesFormatted,
        connectedGuildsFormatted,
        participants.size,
        event.guild!!.id,
        true
    )

    val giveawayName = if (giveaway.title!!.startsWith("sorteio", true)) giveaway.title!! else "sorteio de ${giveaway.title!!}"
    val containerResponse: Container

    // LÓGICA DE RESPOSTA UNIFICADA
    if (winners.isNullOrEmpty()) {
        containerResponse = ComponentBuilder.ContainerBuilder.create()
            .withColor(Colors.DANGER)
            .addText("${Icon.static.get("Eris_cry")} | O **$giveawayName** foi finalizado mais cedo pelo ${userMention(event.user.id)} mas infelizmente não teve participantes válidos!")
            .build()

        event.rapidContainerReply(
            Colors.DANGER,
            "${Icon.static.get("Eris_cry")} | O **$giveawayName** foi finalizado sem vencedores válidos!"
        )
    } else {
        val winnerText = if (winners.size == 1) "o vencedor foi" else "os vencedores foram"
        val winnersMentions = winners.joinToString(", ") { "**<@${it.userid}>**" }

        containerResponse = ComponentBuilder.ContainerBuilder.create()
            .withColor(Colors.SUCCESS)
            .addText("${Icon.static.get("Eris_cry")} | O **$giveawayName** foi finalizado mais cedo pelo ${userMention(event.user.id)}! $winnerText: $winnersMentions")
            .build()

        event.rapidContainerReply(
            Colors.SUCCESS,
            "${Icon.static.get("Eris_cry")} | O **$giveawayName** foi finalizado com sucesso!"
        )
    }

    // ATUALIZAÇÃO DAS MENSAGENS (Fire-and-wait)
    // Usamos launch para executar, e joinAll (via o scopo pai) garante que termine
    coroutineScope {
        messages.map { m ->
            launch {
                try {
                    m.editMessageComponents(menu).useComponentsV2().await()
                    m.replyComponents(containerResponse).useComponentsV2().await()
                } catch (_: Exception) {
                    // Ignora falhas de UI (ex: permissão removida)
                }
            }
        }
        // O coroutineScope aguardará automaticamente todos os launches filhos terminarem
    }
}