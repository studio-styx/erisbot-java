package studio.styx.erisbot.discord.features.commands.moderation.giveaway.subCommands

import dev.minn.jda.ktx.coroutines.await
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import org.jooq.DSLContext
import shared.Colors
import shared.utils.Icon
import studio.styx.erisbot.core.extensions.jda.reply.rapidContainerReply
import studio.styx.erisbot.functions.giveaway.GiveawaySelectWinner
import studio.styx.erisbot.generated.tables.records.GiveawayRecord
import studio.styx.erisbot.generated.tables.records.GuildgiveawayRecord
import studio.styx.erisbot.generated.tables.references.GIVEAWAY
import studio.styx.erisbot.generated.tables.references.GUILDGIVEAWAY
import studio.styx.erisbot.generated.tables.references.ROLEMULTIPLEENTRY
import studio.styx.erisbot.generated.tables.references.USERGIVEAWAY
import studio.styx.schemaEXtended.core.schemas.NumberSchema
import studio.styx.schemaEXtended.core.schemas.ObjectSchema
import studio.styx.schemaEXtended.core.schemas.StringSchema

suspend fun rerollGiveawayCommand(event: SlashCommandInteractionEvent, dsl: DSLContext) {
    val schema = ObjectSchema()
        .addProperty("giveaway", NumberSchema()
            .integer()
            .parseError("Id de sorteio inválido")
            .coerce()
        )
        .addProperty("user", StringSchema()
            .parseError("Id de usuário inválido")
            .minLength(4)
            .minLengthError("O tamanho do id é muito pequeno!")
        )

    val result = schema.parseOrThrow(mapOf(
        "giveaway" to event.getOption("giveaway")!!.asString,
        "user" to event.getOption("user")!!.asString
    ))

    val giveawayId = result.getInteger("giveaway")
    val userId = result.getString("user")

    event.deferReply().await()

    val giveawayRecord = dsl.select(GIVEAWAY.asterisk(), GUILDGIVEAWAY.asterisk())
        .from(GIVEAWAY)
        .join(GUILDGIVEAWAY).on(GIVEAWAY.ID.eq(GUILDGIVEAWAY.GIVEAWAYID))
        .where(GIVEAWAY.ID.eq(giveawayId))
        .and(GUILDGIVEAWAY.GUILDID.eq(event.guild!!.id))
        .fetchOne()

    if (giveawayRecord == null) {
        event.rapidContainerReply(Colors.DANGER,
            "${Icon.static.get("denied")} | Procurei por toda parte, mas não consegui encontrar esse sorteio!"
        )
        return
    }

    if (giveawayRecord.get(GIVEAWAY.ENDED) == false) {
        event.rapidContainerReply(Colors.DANGER,
            "${Icon.static.get("denied")} | Esse sorteio ainda não terminou"
        )
    }

    val participants = dsl.selectFrom(USERGIVEAWAY)
        .where(USERGIVEAWAY.GIVEAWAYID.eq(giveawayId))
        .fetch()

    val participant = participants.find { it.userid == userId }

    if (participant == null) {
        event.rapidContainerReply(Colors.DANGER,
            "${Icon.static.get("error")} | Esse usuário não existe ou não faz parte desse sorteio!"
        )
        return
    }

    if (participant.iswinner == false) {
        event.rapidContainerReply(Colors.DANGER,
            "${Icon.static.get("error")} | Esse usuário não ganhou o sorteio!"
        )
    }

    val connectedGuilds = dsl.selectFrom(GUILDGIVEAWAY)
        .where(GUILDGIVEAWAY.GIVEAWAYID.eq(giveawayId))
        .fetch()
    val roleEntries = dsl.selectFrom(ROLEMULTIPLEENTRY)
        .where(ROLEMULTIPLEENTRY.GIVEAWAYID.eq(giveawayId))
        .fetch()

    val newWinner = GiveawaySelectWinner(
        event.jda,
        dsl,
        giveawayRecord,
        participants,
        connectedGuilds,
        roleEntries
    ).select()?.firstOrNull() ?: run {
        event.rapidContainerReply(
            Colors.DANGER,
            "${Icon.static.get("error")} | Não existe outros usuários que se adequem aos requisitos para possuir o lugar desse usuário!"
        )
        return
    }

    dsl.transaction { config ->
        val tx = config.dsl()

        tx.update(USERGIVEAWAY)
            .set(USERGIVEAWAY.ISWINNER, false)
            .where(USERGIVEAWAY.GIVEAWAYID.eq(giveawayId))
            .and(USERGIVEAWAY.USERID.eq(userId))
            .execute()
        tx.update(USERGIVEAWAY)
            .set(USERGIVEAWAY.ISWINNER, true)
            .where(USERGIVEAWAY.GIVEAWAYID.eq(giveawayId))
            .and(USERGIVEAWAY.USERID.eq(newWinner.userid))
            .execute()
        tx.select(USERGIVEAWAY)
            .where(USERGIVEAWAY.GIVEAWAYID.eq(giveawayId))
            .and(USERGIVEAWAY.ISWINNER.eq(true))
            .fetch()

    }
}