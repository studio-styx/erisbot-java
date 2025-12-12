package studio.styx.erisbot.discord.features.commands.moderation.giveaway.subCommands

import database.extensions.giveaway
import dev.minn.jda.ktx.coroutines.await
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import org.jooq.DSLContext
import discord.extensions.jda.reply.rapidContainerReply
import studio.styx.schemaEXtended.core.schemas.NumberSchema
import shared.Colors
import shared.utils.Icon
import utils.ComponentBuilder.ContainerBuilder

private val CANCEL_SCHEMA = NumberSchema()
    .integer()
    .parseError("Id de sorteio inválido")
    .coerce()

suspend fun cancelGiveawayCommand(event: SlashCommandInteractionEvent, dsl: DSLContext) {
    val giveawayId = CANCEL_SCHEMA.parseOrThrow(event.getOption("id")?.asString).toInt()

    event.deferReply().await()

    val data = dsl.giveaway(giveawayId).withConnectedGuilds().fetch()

    if (data == null) {
        event.rapidContainerReply(Colors.DANGER, "${Icon.static.get("denied")} | Sorteio não encontrado!")
        return
    }

    val giveawayRecord = data.giveaway
    val connectedGuilds = data.connectedGuilds!!


    if (giveawayRecord.ended == true) {
        event.rapidContainerReply(Colors.DANGER, "${Icon.static.get("denied")} | Este sorteio já foi encerrado/cancelado!")
        return
    }

    val guildConnected = connectedGuilds.find { it.guildid == event.guild!!.id }
    if (guildConnected == null) {
        event.rapidContainerReply(Colors.DANGER, "${Icon.static.get("denied")} | Este sorteio não está conectado a esse server!")
        return
    }

    val guildIsHost = guildConnected.ishost!!

    val message = "${Icon.static.get("Eris_cry")} | Você está prestes a excluir o sorteio **${giveawayRecord.title}** ${when {
        guildIsHost && connectedGuilds.size > 1 -> 
            "como esse é um servidor host do sorteio, ao excluir ele todos os outros sorteio(s) de outro(s) **${connectedGuilds.size}** server(s) também será apagado"
        !guildIsHost && connectedGuilds.size > 1 ->
            "como esse é um sorteio conectado a outro(s) server(s) e ele não é host, apenas esse server sairá do sorteio, enquanto os outros continuarão funcionando normalmente"
        else -> "Você tem certeza que deseja apagar esse sorteio?"
    }}"

    val container = ContainerBuilder.create()
        .withColor(Colors.WARNING)
        .addText(message)
        .addRow(ActionRow.of(
            Button.danger("giveaway/delete/confirm/${giveawayId}/${event.user.id}", "Deletar").withEmoji(Emoji.fromUnicode("🗑️")),
            Button.secondary("giveaway/delete/cancel/${giveawayId}/${event.user.id}", "Cancelar").withEmoji(Emoji.fromUnicode("❌"))
        ))
        .build()

    event.hook.editOriginalComponents(container).await()
}