package studio.styx.erisbot.discord.features.commands.moderation.giveaway.subCommands

import dev.minn.jda.ktx.coroutines.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import org.jooq.DSLContext
import shared.Colors
import shared.utils.Icon
import studio.styx.erisbot.core.extensions.jda.guilds.giveawayEntryEndPoints.giveawayEntryPoints
import studio.styx.erisbot.core.extensions.jda.reply.rapidContainerReply
import studio.styx.erisbot.discord.menus.giveaway.GiveawayMenuConnectedGuildExpectedValues
import studio.styx.erisbot.discord.menus.giveaway.giveawayMenu
import studio.styx.erisbot.functions.giveaway.getGiveawayRoleEntriesFormatted
import studio.styx.erisbot.generated.tables.records.GiveawayRecord
import studio.styx.erisbot.generated.tables.records.GuildgiveawayRecord
import studio.styx.erisbot.generated.tables.records.RolemultipleentryRecord
import studio.styx.erisbot.generated.tables.references.GIVEAWAY
import studio.styx.erisbot.generated.tables.references.GUILDGIVEAWAY
import studio.styx.erisbot.generated.tables.references.ROLEMULTIPLEENTRY
import studio.styx.erisbot.generated.tables.references.USERGIVEAWAY
import studio.styx.schemaEXtended.core.schemas.NumberSchema

private data class GiveawayData(
    val giveaway: GiveawayRecord?,
    val participants: Int,
    val connectedGuilds: List<GuildgiveawayRecord>,
    val roleEntries: List<RolemultipleentryRecord>
)

private val GIVEAWAY_ENTRY_SCHEMA = NumberSchema()
    .parseError("Id de sorteio inválido")
    .min(1)
    .minError("Id de sorteio muito baixo")
    .coerce()

suspend fun giveawayEntryCommand(event: SlashCommandInteractionEvent, dsl: DSLContext) {
    val giveawayId = GIVEAWAY_ENTRY_SCHEMA.parseOrThrow(event.getOption("id")?.asString).toInt()
    val channel = event.getOption("channel")!!.asChannel

    event.deferReply().await()
    val guild = event.guild!!

    val guildInvites = guild.giveawayEntryPoints.getEntryInvites()
    val invite = guildInvites.find { it.giveawayId == giveawayId } ?: run {
        event.rapidContainerReply(
            Colors.DANGER,
            "${Icon.static.get("error")} | Não consegui encontrar esse convite!"
        )
        return
    }

    event.jda.getGuildById(invite.inviterGuildId) ?: run {
        guild.giveawayEntryPoints.removeEntryInvite(invite)
        event.rapidContainerReply(
            Colors.DANGER,
            "${Icon.static.get("error")} | Não consegui encontrar o servidor de convite!"
        )
        return
    }

    val selfHasSendMessagePermission = guild.selfMember.hasPermission(channel, Permission.MESSAGE_SEND)
    val selfHasEmbedLinkPermission = guild.selfMember.hasPermission(channel, Permission.MESSAGE_EMBED_LINKS)
    val userHasPermission = event.member!!.hasPermission(channel, Permission.MESSAGE_SEND)

    val errors = mutableListOf<String>()
    if (!selfHasSendMessagePermission) errors.add("Eu não tenho a permissão pra enviar mensagens nesse canal")
    if (!selfHasEmbedLinkPermission) errors.add("Eu não tenho a permissão pra enviar links nesse canal")
    if (!userHasPermission) errors.add("Você não tem a permissão pra enviar mensagens nesse canal")

    if (errors.isNotEmpty()) {
        event.rapidContainerReply(
            Colors.DANGER,
            "${Icon.static.get("error")} | ${errors.joinToString("\n")}"
        )
        return
    }

    val result = coroutineScope {
        val giveawayDeferred = async(Dispatchers.IO) {
            dsl.selectFrom(GIVEAWAY)
                .where(GIVEAWAY.ID.eq(giveawayId))
                .fetchOne()
        }

        val participantsDeferred = async(Dispatchers.IO) {
            dsl.fetchCount(USERGIVEAWAY, USERGIVEAWAY.GIVEAWAYID.eq(giveawayId))
        }

        val connectedGuildsDeferred = async(Dispatchers.IO) {
            dsl.selectFrom(GUILDGIVEAWAY)
                .where(GUILDGIVEAWAY.GIVEAWAYID.eq(giveawayId))
                .fetch()
        }

        val roleEntriesDeferred = async(Dispatchers.IO) {
            dsl.selectFrom(ROLEMULTIPLEENTRY)
                .where(ROLEMULTIPLEENTRY.GIVEAWAYID.eq(giveawayId))
                .fetch()
        }

        GiveawayData(
            giveaway = giveawayDeferred.await(),
            participants = participantsDeferred.await(),
            connectedGuilds = connectedGuildsDeferred.await(),
            roleEntries = roleEntriesDeferred.await()
        )
    }

    val (giveaway, participants, connectedGuilds, roleEntries) = result

    if (giveaway == null) {
        event.rapidContainerReply(
            Colors.DANGER,
            "${Icon.static.get("error")} | Não consegui encontrar esse sorteio!"
        )
        return
    }

    if (giveaway.ended == true) {
        event.rapidContainerReply(
            Colors.DANGER,
            "${Icon.static.get("error")} | Esse sorteio já terminou!"
        )
        return
    }

    if (connectedGuilds.find { it.guildid == guild.id } != null) {
        event.rapidContainerReply(
            Colors.DANGER,
            "${Icon.static.get("error")} | Esse sorteio já está conectado nesse servidor!"
        )
        return
    }

    val rolesEntriesFormatted = getGiveawayRoleEntriesFormatted(
        roleEntries,
        connectedGuilds,
        event.jda
    )

    val connectedGuildsFormatted = connectedGuilds.map { cnn ->
        val guild = event.jda.getGuildById(cnn.guildid!!) ?: return@map null

        GiveawayMenuConnectedGuildExpectedValues(
            guild.name,
            cnn
        )
    }.filterNotNull()

    val menu = giveawayMenu(
        giveaway,
        rolesEntriesFormatted,
        connectedGuildsFormatted,
        participants,
        guild.id,
    )

    channel.asTextChannel().sendMessageComponents(menu).useComponentsV2().await()
    guild.giveawayEntryPoints.removeEntryInvite(invite)
    event.rapidContainerReply(Colors.SUCCESS, "${Icon.static.get("success")} | Sorteio foi conectado com sucesso no canal **${channel.id}**")
}