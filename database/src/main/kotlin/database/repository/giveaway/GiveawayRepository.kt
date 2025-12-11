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

class GiveawayRepository(private val dsl: DSLContext) {
    fun findById(id: Int) = dsl.selectFrom(GIVEAWAY).where(GIVEAWAY.ID.eq(id)).fetchOne()
    fun findAll() = dsl.selectFrom(GIVEAWAY).fetch()
    fun findAllByGuild(guildId: String) =
        dsl.select(GIVEAWAY.asterisk())
            .from(GIVEAWAY)
            .innerJoin(GUILDGIVEAWAY).on(GIVEAWAY.ID.eq(GUILDGIVEAWAY.GIVEAWAYID))
            .where(GUILDGIVEAWAY.GUILDID.eq(guildId))
            .fetch()

    fun delete(id: Int) = dsl.deleteFrom(GIVEAWAY).where(GIVEAWAY.ID.eq(id)).execute()

    fun update(record: GiveawayRecord) = dsl.update(GIVEAWAY).set(record).where(GIVEAWAY.ID.eq(record.id)).execute()

    private suspend fun fetchGiveawayComponents(id: Int): GiveawayComponents = coroutineScope {
        val giveawayDeferred = async(Dispatchers.IO) { findById(id) }
        val participantsDeferred = async(Dispatchers.IO) {
            dsl.selectFrom(USERGIVEAWAY).where(USERGIVEAWAY.GIVEAWAYID.eq(id)).fetch()
        }
        val connectedGuildsDeferred = async(Dispatchers.IO) {
            dsl.selectFrom(GUILDGIVEAWAY).where(GUILDGIVEAWAY.GIVEAWAYID.eq(id)).fetch()
        }
        val roleEntriesDeferred = async(Dispatchers.IO) {
            dsl.selectFrom(ROLEMULTIPLEENTRY).where(ROLEMULTIPLEENTRY.GIVEAWAYID.eq(id)).fetch()
        }

        GiveawayComponents(
            giveaway = giveawayDeferred.await(),
            participants = participantsDeferred.await(),
            connectedGuilds = connectedGuildsDeferred.await(),
            roleEntries = roleEntriesDeferred.await()
        )
    }

    suspend fun getGiveawayFull(id: Int): FullGiveawayReturns? {
        val components = fetchGiveawayComponents(id)
        return components.giveaway?.let {
            FullGiveawayReturns(
                giveaway = it,
                participants = components.participants,
                connectedGuilds = components.connectedGuilds,
                roleEntries = components.roleEntries
            )
        }
    }

    suspend fun getGiveawayWithParticipantsCount(id: Int): FullGiveawayReturnsWithParticipantsCount? {
        val result = coroutineScope {
            val giveawayDeferred = async(Dispatchers.IO) {
                dsl.selectFrom(GIVEAWAY)
                    .where(GIVEAWAY.ID.eq(id))
                    .fetchOne()
            }

            val participantsCountDeferred = async(Dispatchers.IO) {
                dsl.fetchCount(USERGIVEAWAY, USERGIVEAWAY.GIVEAWAYID.eq(id))
            }

            val connectedGuildsDeferred = async(Dispatchers.IO) {
                dsl.selectFrom(GUILDGIVEAWAY)
                    .where(GUILDGIVEAWAY.GIVEAWAYID.eq(id))
                    .fetch()
            }

            val roleEntriesDeferred = async(Dispatchers.IO) {
                dsl.selectFrom(ROLEMULTIPLEENTRY)
                    .where(ROLEMULTIPLEENTRY.GIVEAWAYID.eq(id))
                    .fetch()
            }

            GiveawayComponentsWithParticipantsCount(
                giveaway = giveawayDeferred.await(),
                participants = participantsCountDeferred.await(),
                connectedGuilds = connectedGuildsDeferred.await(),
                roleEntries = roleEntriesDeferred.await()
            )
        }

        return result.giveaway?.let {
            FullGiveawayReturnsWithParticipantsCount(
                giveaway = it,
                participantsCount = result.participants,
                connectedGuilds = result.connectedGuilds,
                roleEntries = result.roleEntries
            )
        }
    }

    fun buildQuery(id: Int) = GiveawayQueryBuilder(dsl, id)

    private data class GiveawayComponentsWithParticipantsCount(
        val giveaway: GiveawayRecord?,
        val participants: Int,
        val connectedGuilds: List<GuildgiveawayRecord>,
        val roleEntries: List<RolemultipleentryRecord>
    )

    private data class GiveawayComponents(
        val giveaway: GiveawayRecord?,
        val participants: List<UsergiveawayRecord>,
        val connectedGuilds: List<GuildgiveawayRecord>,
        val roleEntries: List<RolemultipleentryRecord>
    )
}