package studio.styx.erisbot.functions.giveaway

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Role
import studio.styx.erisbot.discord.menus.giveaway.GiveawayMenuRoleEntriesExpectedValues
import studio.styx.erisbot.generated.tables.records.GuildgiveawayRecord
import studio.styx.erisbot.generated.tables.records.RolemultipleentryRecord

suspend fun getGiveawayRoleEntriesFormatted(
    roleEntries: List<RolemultipleentryRecord>,
    connectedGuilds: List<GuildgiveawayRecord>,
    jda: JDA
): List<GiveawayMenuRoleEntriesExpectedValues> {
    val rolesEntriesFormatted = coroutineScope {
        roleEntries.map { r ->
            async {
                var role: Role? = null
                for (cnnGuild in connectedGuilds) {
                    val guild = jda.getGuildById(cnnGuild.guildid!!) ?: continue

                    val foundedRole = guild.getRoleById(r.roleid!!)
                    if (foundedRole != null) {
                        role = foundedRole
                        break
                    }
                }

                if (role == null) return@async null

                GiveawayMenuRoleEntriesExpectedValues(
                    roleName = role.name,
                    role = r
                )
            }
        }.awaitAll()
    }

    return rolesEntriesFormatted.filterNotNull()
}