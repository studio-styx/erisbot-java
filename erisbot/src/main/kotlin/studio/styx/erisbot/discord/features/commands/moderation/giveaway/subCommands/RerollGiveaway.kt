package studio.styx.erisbot.discord.features.commands.moderation.giveaway.subCommands

import dev.minn.jda.ktx.coroutines.await
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import org.jooq.DSLContext
import org.jooq.impl.DSL
import shared.Colors
import shared.utils.Icon
import shared.utils.MentionUtil.userMention
import studio.styx.erisbot.core.extensions.jda.reply.rapidContainerReply
import studio.styx.erisbot.functions.giveaway.GiveawaySelectWinner
import studio.styx.erisbot.generated.tables.records.GuildgiveawayRecord
import studio.styx.erisbot.generated.tables.references.GIVEAWAY
import studio.styx.erisbot.generated.tables.references.GUILDGIVEAWAY
import studio.styx.erisbot.generated.tables.references.ROLEMULTIPLEENTRY
import studio.styx.erisbot.generated.tables.references.USERGIVEAWAY
import studio.styx.schemaEXtended.core.schemas.NumberSchema
import studio.styx.schemaEXtended.core.schemas.ObjectSchema
import studio.styx.schemaEXtended.core.schemas.StringSchema
import utils.ComponentBuilder
import kotlinx.coroutines.Dispatchers
private val REROLL_SCHEMA = ObjectSchema()
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

suspend fun rerollGiveawayCommand(event: SlashCommandInteractionEvent, dsl: DSLContext) {
    // Validação de inputs
    val optionsMap = mapOf(
        "giveaway" to event.getOption("giveaway")?.asString,
        "user" to event.getOption("user")?.asString
    )

    val result = REROLL_SCHEMA.parseOrThrow(optionsMap)

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
        event.rapidContainerReply(Colors.DANGER, "${Icon.static.get("denied")} | Sorteio não encontrado!")
        return
    }

    if (giveawayRecord.get(GIVEAWAY.ENDED) == false) {
        event.rapidContainerReply(Colors.DANGER, "${Icon.static.get("denied")} | Esse sorteio ainda não terminou")
        return
    }

    val targetUserRecord = dsl.selectFrom(USERGIVEAWAY)
        .where(USERGIVEAWAY.GIVEAWAYID.eq(giveawayId))
        .and(USERGIVEAWAY.USERID.eq(userId))
        .fetchOne()

    if (targetUserRecord == null) {
        event.rapidContainerReply(Colors.DANGER, "${Icon.static.get("error")} | Esse usuário não participa desse sorteio!")
        return
    }

    // Bug corrigido: Adicionado return
    if (targetUserRecord.iswinner == false) {
        event.rapidContainerReply(Colors.DANGER, "${Icon.static.get("error")} | Esse usuário não ganhou o sorteio!")
        return
    }

    // 4. Agora que tudo foi validado, carregamos os dados pesados
    val (participants, connectedGuilds, roleEntries) = coroutineScope {
        val participantsDeferred = async(Dispatchers.IO) {
            dsl.selectFrom(USERGIVEAWAY)
                .where(USERGIVEAWAY.GIVEAWAYID.eq(giveawayId))
                .fetch()
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

        // Aguarda os 3 terminarem e retorna uma Triple ou Destructuring
        Triple(
            participantsDeferred.await(),
            connectedGuildsDeferred.await(),
            roleEntriesDeferred.await()
        )
    }

    // Lógica de seleção
    val newWinner = GiveawaySelectWinner(
        event.jda,
        dsl,
        giveawayRecord,
        participants,
        connectedGuilds,
        roleEntries
    ).select()?.firstOrNull { it.userid != userId } // Garante que não seleciona o mesmo usuário caso a lógica interna não filtre

    if (newWinner == null) {
        event.rapidContainerReply(
            Colors.DANGER,
            "${Icon.static.get("error")} | Não há outros participantes elegíveis para assumir o lugar!"
        )
        return
    }

    // Transação
    dsl.transaction { config ->
        val tx = config.dsl()
        tx.update(USERGIVEAWAY)
            .set(
                USERGIVEAWAY.ISWINNER,
                DSL.`when`(USERGIVEAWAY.USERID.eq(newWinner.userid), true)
                    .otherwise(false)
            )
            .where(USERGIVEAWAY.GIVEAWAYID.eq(giveawayId))
            .and(USERGIVEAWAY.USERID.`in`(userId, newWinner.userid))
            .execute()
    }

    // Notificações em paralelo
    coroutineScope {
        connectedGuilds.forEach { cg ->
            async {
                sendAllGiveawaysNewWinners(
                    event, cg,
                    event.user.id,
                    userId,
                    newWinner.userid!!
                )
            }
        }
    }

    event.rapidContainerReply(Colors.SUCCESS,
        "${Icon.static.get("success")} | Ganhador trocado: **${userMention(userId)}** ➔ **${userMention(newWinner.userid!!)}**")
}

private suspend fun sendAllGiveawaysNewWinners(
    event: SlashCommandInteractionEvent,
    connectedGuild: GuildgiveawayRecord,
    moderatorId: String,
    oldWinnerId: String,
    newWinnerId: String
) {
    try {
        val guild = event.jda.getGuildById(connectedGuild.guildid!!) ?: return
        val channel = guild.getTextChannelById(connectedGuild.channelid!!) ?: return

        val substitutionMessage = "${Icon.static.get("warning")} | O moderador: ${userMention(moderatorId)} refez o sorteio!\n" +
                "Saiu: **${userMention(oldWinnerId)}**\n" +
                "Entrou: **${userMention(newWinnerId)}**"

        val container = ComponentBuilder.ContainerBuilder.create()
            .withColor(Colors.WARNING)
            .addText(substitutionMessage)
            .build()

        // Tenta buscar a mensagem, se falhar ou for nula, envia uma nova
        val messageId = connectedGuild.messageid
        if (messageId != null) {
            try {
                val message = channel.retrieveMessageById(messageId).await()
                message.replyComponents(container).useComponentsV2().await()
                return // Sucesso, sai da função
            } catch (_: Exception) {

            }
        }

        // Envia nova mensagem caso a resposta falhe
        channel.sendMessageComponents(container).useComponentsV2().await()

    } catch (_: Exception) {

    }
}