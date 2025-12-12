package schedules

import database.extensions.giveaways
import dev.minn.jda.ktx.coroutines.await
import functions.giveaway.GiveawaySelectWinner
import functions.giveaway.getGiveawayRoleEntriesFormatted
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import menus.giveaway.GiveawayMenuConnectedGuildExpectedValues
import menus.giveaway.giveawayMenu
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.components.container.Container
import org.jooq.DSLContext
import shared.Colors
import shared.utils.Icon
import studio.styx.erisbot.generated.tables.records.GiveawayRecord
import studio.styx.erisbot.generated.tables.references.GIVEAWAY
import studio.styx.erisbot.generated.tables.references.USERGIVEAWAY
import utils.ComponentBuilder
import java.time.LocalDateTime
import java.time.ZoneOffset

class GiveawayExpires(private val jda: JDA, private val dsl: DSLContext) {
    fun findNextGiveawaysExpires(): List<GiveawayRecord> =
        dsl.selectFrom(GIVEAWAY)
            .where(GIVEAWAY.ENDED.eq(false))
            .and(GIVEAWAY.EXPIRESAT.greaterThan(LocalDateTime.now()))
            .and(GIVEAWAY.EXPIRESAT.lessThan(LocalDateTime.now().plusMinutes(10)))
            .fetch()

    suspend fun scheduleGiveawayExpires(giveawayId: Int, expiresAt: LocalDateTime) {
        val now = LocalDateTime.now().atOffset(ZoneOffset.UTC).toInstant()
        val remainingMilliseconds = expiresAt.atOffset(ZoneOffset.UTC).toInstant().toEpochMilli() - now.toEpochMilli()

        delay(remainingMilliseconds)

        executeEndGiveaway(giveawayId)
    }

    suspend fun executeEndGiveaway(giveawayId: Int) {
        val (giveaway, participants, connectedGuilds, roleEntries) = dsl.giveaways.getGiveawayFull(giveawayId) ?: return

        if (giveaway.expiresat!!.isAfter(LocalDateTime.now())) return

        val (winners, roleEntriesFormatted, messages) = coroutineScope {
            val winnersDeferred = async(Dispatchers.Default) {
                GiveawaySelectWinner(
                    jda, dsl, giveaway, participants, connectedGuilds, roleEntries, giveaway.userswins ?: 1
                ).select()
            }

            val roleEntriesFormattedDeferred = async(Dispatchers.Default) {
                getGiveawayRoleEntriesFormatted(roleEntries, connectedGuilds, jda)
            }

            val messagesDeferred = async(Dispatchers.IO) {
                connectedGuilds.map { cnn ->
                    async {
                        runCatching {
                            val guild = jda.getGuildById(cnn.guildid!!) ?: return@runCatching null
                            val channel = guild.getTextChannelById(cnn.channelid!!) ?: return@runCatching null
                            val messageId = cnn.messageid ?: return@runCatching null
                            channel.retrieveMessageById(messageId).await()
                        }.getOrNull() // Retorna null se falhar ao buscar a mensagem (ex: deletada)
                    }
                }.awaitAll().filterNotNull()
            }

            Triple(winnersDeferred.await(), roleEntriesFormattedDeferred.await(), messagesDeferred.await())
        }

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

        val connectedGuildsFormatted = connectedGuilds.mapNotNull { cnn ->
            val guild = jda.getGuildById(cnn.guildid!!) ?: return@mapNotNull null
            GiveawayMenuConnectedGuildExpectedValues(guild.name, cnn)
        }

        val giveawayName = if (giveaway.title!!.startsWith("sorteio", true)) giveaway.title!! else "sorteio de ${giveaway.title!!}"
        val containerResponse: Container

        // LÓGICA DE RESPOSTA UNIFICADA
        if (winners.isNullOrEmpty()) {
            containerResponse = ComponentBuilder.ContainerBuilder.create()
                .withColor(Colors.DANGER)
                .addText("${Icon.static.get("Eris_cry")} | O **$giveawayName** foi finalizado! mas infelizmente não houve participantes válidos!")
                .build()
        } else {
            val winnerText = if (winners.size == 1) "o vencedor foi" else "os vencedores foram"
            val winnersMentions = winners.joinToString(", ") { "**<@${it.userid}>**" }

            containerResponse = ComponentBuilder.ContainerBuilder.create()
                .withColor(Colors.SUCCESS)
                .addText("${Icon.static.get("Eris_cry")} | O **$giveawayName** foi finalizado com sucesso! $winnerText: $winnersMentions")
                .build()
        }

        coroutineScope {
            messages.map { m ->
                launch {
                    try {
                        val menu = giveawayMenu(
                            giveaway,
                            roleEntriesFormatted,
                            connectedGuildsFormatted,
                            participants.size,
                            m.guild.id
                        )
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

    suspend fun scheduleGiveaway() {
        val giveaways = findNextGiveawaysExpires()

        if (giveaways.isEmpty()) return

        coroutineScope {
            giveaways.forEach { giveaway ->
                launch {
                    scheduleGiveawayExpires(giveaway.id!!, giveaway.expiresat!!)
                }
            }
        }
    }

    suspend fun infinitelyScheduleGiveaway() {
        while (true) {
            try {
                scheduleGiveaway()
            } catch (e: Exception) {
                println("Erro ao agendar sorteios: ${e.message}")
                e.printStackTrace()
            }

            try {
                delay(10000)
            } catch (e: CancellationException) {
                // Coroutine foi cancelada - sai do loop
                println("Schedule de sorteios finalizado")
                break
            }
        }
    }
}