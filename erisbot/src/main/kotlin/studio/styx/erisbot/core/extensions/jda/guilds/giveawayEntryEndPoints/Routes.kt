package studio.styx.erisbot.core.extensions.jda.guilds.giveawayEntryEndPoints

import net.dv8tion.jda.api.entities.Guild
import redis.RedisManager
import studio.styx.erisbot.core.dtos.giveaway.entry.GiveawayEntryInvite
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.util.concurrent.TimeUnit

val Guild.giveawayEntryPoints: GiveawayEntryEndPoints
    get() = GiveawayEntryEndPoints(this)

class GiveawayEntryEndPoints(private val guild: Guild) {
    private val key = "giveaways:invites:${guild.id}"
    private val redis = RedisManager
    private val objectMapper = jacksonObjectMapper()

    suspend fun getEntryInvites(): List<GiveawayEntryInvite> {
        val raw = redis.get(key) ?: return emptyList()

        return try {
            objectMapper.readValue(raw)
        } catch (e: Exception) {
            println("Erro ao decodificar invites do Redis: ${e.message}")
            emptyList()
        }
    }

    suspend fun addEntryInvite(invite: GiveawayEntryInvite) {
        val invites = getEntryInvites().toMutableList()
        invites.add(invite)
        saveInvites(invites)
    }

    suspend fun removeEntryInvite(invite: GiveawayEntryInvite) {
        val invites = getEntryInvites().toMutableList()
        invites.remove(invite)
        saveInvites(invites)
    }

    suspend fun clearEntryInvites() {
        redis.delete(key)
    }

    private suspend fun saveInvites(invites: List<GiveawayEntryInvite>) {
        val jsonString = objectMapper.writeValueAsString(invites)
        redis.set(key, jsonString, 24, TimeUnit.HOURS)
    }
}