package studio.styx.erisbot.discord.features.commands.moderation.giveaway.subCommands

import database.extensions.personalization.getContainerInfo
import dev.minn.jda.ktx.coroutines.await
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import org.jooq.DSLContext
import shared.Colors
import shared.utils.Icon
import studio.styx.erisbot.core.extensions.jda.guilds.giveawayEntryEndPoints.giveawayEntryPoints
import studio.styx.erisbot.core.extensions.jda.reply.rapidContainerReply
import studio.styx.erisbot.generated.tables.references.GIVEAWAY
import studio.styx.schemaEXtended.core.schemas.NumberSchema

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

    val inviter = event.jda.getGuildById(invite.inviterGuildId) ?: run {
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

    val giveaway = dsl.selectFrom(GIVEAWAY)
        .where(GIVEAWAY.ID.eq(giveawayId))
        .fetchOne() ?: run {
            event.rapidContainerReply(
                Colors.DANGER,
                "${Icon.static.get("error")} | Não consegui encontrar esse sorteio!"
            )
            return
        }

    try {
        val textChannel = channel.asTextChannel()

        val isContainer = giveaway.containerid != null

        if (isContainer) {
            val containerInfo = dsl.getContainerInfo(giveaway.containerid!!)


        }
    }
}