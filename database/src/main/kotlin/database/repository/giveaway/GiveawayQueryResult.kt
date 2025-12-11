package database.repository.giveaway

import studio.styx.erisbot.generated.tables.records.GiveawayRecord
import studio.styx.erisbot.generated.tables.records.GuildgiveawayRecord
import studio.styx.erisbot.generated.tables.records.RolemultipleentryRecord
import studio.styx.erisbot.generated.tables.records.UsergiveawayRecord

data class GiveawayQueryResult(
    val giveaway: GiveawayRecord,
    val participants: List<UsergiveawayRecord>?,
    val connectedGuilds: List<GuildgiveawayRecord>?,
    val roleEntries: List<RolemultipleentryRecord>?
)