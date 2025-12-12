package menus.giveaway

import net.dv8tion.jda.api.components.MessageTopLevelComponent
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import shared.Colors
import shared.utils.DiscordTimeStyle
import shared.utils.Icon
import shared.utils.MentionUtil.roleMention
import shared.utils.Utils
import shared.utils.Utils.brBuilder
import studio.styx.erisbot.generated.tables.records.GiveawayRecord
import studio.styx.erisbot.generated.tables.records.GuildgiveawayRecord
import studio.styx.erisbot.generated.tables.records.RolemultipleentryRecord
import utils.ComponentBuilder

data class GiveawayMenuRoleEntriesExpectedValues(
    val roleName: String,
    val role: RolemultipleentryRecord
)

data class GiveawayMenuConnectedGuildExpectedValues(
    val guildName: String,
    val guild: GuildgiveawayRecord
)

fun giveawayMenu(
    giveaway: GiveawayRecord,
    roleEntries: List<GiveawayMenuRoleEntriesExpectedValues>,
    connectedGuilds: List<GiveawayMenuConnectedGuildExpectedValues>,
    participantsCount: Int,
    guildId: String,
    disableButtons: Boolean = false
): MutableList<MessageTopLevelComponent> {
    val guildInfo = connectedGuilds.find { it.guild.guildid == guildId }?.guild

    val containerBuilder = ComponentBuilder.ContainerBuilder.create()
        .withColor(Colors.FUCHSIA)
        .addText("${Icon.animated.get("confetti")} | **Sorteio ${giveaway.localid}**")
        .addText("## ${giveaway.title}")
        .addDivider()

    if (giveaway.description != null) containerBuilder.addText(giveaway.description).addDivider()

    if (roleEntries.isNotEmpty() || connectedGuilds.isNotEmpty()) {
        val roleEntriesAndConnectedGuildsText = brBuilder(
            if (roleEntries.isNotEmpty())
                        brBuilder(
                            "**${Icon.static.get("ticket2x")} - Cargos que ganham multiplas entradas:**`",
                            roleEntries.joinToString("\n") { r ->
                                "- **${r.roleName} - ${r.role.extraentries}**"
                            }
                        )
                    else null,
            if (connectedGuilds.isNotEmpty())
                brBuilder(
                    "**${Icon.static.get("connect_guilds")} - Servidores que estão conectados ao sorteio:** **`(${connectedGuilds.size})`**",
                    connectedGuilds.joinToString(",  ") {
                        "**`${it.guildName}`**"
                    }
                )
            else null
        )

        containerBuilder.addText(roleEntriesAndConnectedGuildsText).addDivider()
    }

    if ((guildInfo?.blacklistroles?.isNotEmpty() ?: false) || guildInfo?.xprequired != null) {
        val blacklistRolesAndConnectedGuildsText = brBuilder(
            if (guildInfo.blacklistroles?.isNotEmpty() ?: false)
                        brBuilder(
                            "**${Icon.static.get("blacklist")} - Cargos de blacklist:**",
                            guildInfo.blacklistroles!!.joinToString(",  ") {
                                "**`${roleMention(it!!)}`**"
                            }
                        )
                    else null,
            if (guildInfo.xprequired != null)
                brBuilder(
                    "**${Icon.static.get("info")} - Xp exigido:**",
                    guildInfo.xprequired.toString()
                )
            else null
        )

        containerBuilder.addText(blacklistRolesAndConnectedGuildsText).addDivider()
    }

    containerBuilder.addText("${Icon.static.get("alarm")} - Sorteio termina ${Utils.formatDiscordTime(giveaway.expiresat!!, DiscordTimeStyle.RELATIVE)} (${
        Utils.formatDiscordTime(
            giveaway.expiresat!!,
            DiscordTimeStyle.LONGDATETIME
        )
    })")

    return mutableListOf(
        containerBuilder.build(),
        ActionRow.of(
            Button.primary("giveaway/entry/${giveaway.id}", "Entrar (${participantsCount})").withDisabled(disableButtons),
            Button.secondary("giveaway/participants/${giveaway.id}", "Participantes").withDisabled(disableButtons)
        )
    )
}