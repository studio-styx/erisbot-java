package dtos.giveaway.entry

data class GiveawayEntryInvite(
    val inviterGuildId: String,
    val targetGuildId: String,
    val giveawayId: Int
)