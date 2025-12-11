package database.repository.giveaway

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jooq.DSLContext
import studio.styx.erisbot.generated.tables.records.GiveawayRecord
import studio.styx.erisbot.generated.tables.records.GuildgiveawayRecord
import studio.styx.erisbot.generated.tables.records.RolemultipleentryRecord
import studio.styx.erisbot.generated.tables.records.UsergiveawayRecord
import studio.styx.erisbot.generated.tables.references.GIVEAWAY
import studio.styx.erisbot.generated.tables.references.GUILDGIVEAWAY
import studio.styx.erisbot.generated.tables.references.ROLEMULTIPLEENTRY
import studio.styx.erisbot.generated.tables.references.USERGIVEAWAY

class GiveawayQueryBuilder(private val dsl: DSLContext, private val id: Int) {
    private var withConnectedGuilds: Boolean = false
    private var withRoleEntries: Boolean = false
    private var withParticipants: Boolean = false

    fun withConnectedGuilds() = apply { this.withConnectedGuilds = true }
    fun withRoleEntries() = apply { this.withRoleEntries = true }
    fun withParticipants() = apply { this.withParticipants = true }

    private data class PreGiveawayResult(
        val giveaway: GiveawayRecord?,
        val participants: List<UsergiveawayRecord>?,
        val connectedGuilds: List<GuildgiveawayRecord>?,
        val roleEntries: List<RolemultipleentryRecord>?
    )

    suspend fun fetch(): GiveawayQueryResult? = coroutineScope {
        val giveawayDeferred = async(Dispatchers.IO) {
            dsl.selectFrom(GIVEAWAY).where(GIVEAWAY.ID.eq(id)).fetchOne()
        }
        val participantsDeferred = async(Dispatchers.IO) {
            dsl.selectFrom(USERGIVEAWAY).where(USERGIVEAWAY.GIVEAWAYID.eq(id)).fetch()
        }
        val connectedGuildsDeferred = async(Dispatchers.IO) {
            dsl.selectFrom(GUILDGIVEAWAY).where(GUILDGIVEAWAY.GIVEAWAYID.eq(id)).fetch()
        }
        val roleEntriesDeferred = async(Dispatchers.IO) {
            dsl.selectFrom(ROLEMULTIPLEENTRY).where(ROLEMULTIPLEENTRY.GIVEAWAYID.eq(id)).fetch()
        }

        val pre = PreGiveawayResult(
            giveaway = giveawayDeferred.await(),
            participants = if (withParticipants) participantsDeferred.await() else null,
            connectedGuilds = if (withConnectedGuilds) connectedGuildsDeferred.await() else null,
            roleEntries = if (withRoleEntries) roleEntriesDeferred.await() else null
        )

        if (pre.giveaway == null) return@coroutineScope null
        else return@coroutineScope GiveawayQueryResult(
            giveaway = pre.giveaway,
            participants = pre.participants,
            connectedGuilds = pre.connectedGuilds,
            roleEntries = pre.roleEntries
        )
    }
}