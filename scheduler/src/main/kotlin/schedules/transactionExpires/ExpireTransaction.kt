package schedules.transactionExpires

import net.dv8tion.jda.api.JDA
import org.jooq.DSLContext
import shared.Colors
import shared.utils.Icon
import studio.styx.erisbot.generated.enums.Transactionstatus
import studio.styx.erisbot.generated.tables.records.TransactionRecord
import studio.styx.erisbot.generated.tables.references.TRANSACTION
import translates.LanguageUtils
import utils.ComponentBuilder

fun expireTransaction(transaction: TransactionRecord, jda: JDA, dsl: DSLContext) {
    // Pegar de novo a transação para ter certeza que ela não foi aprovada depois do tempo
    val transaction = dsl.selectFrom(TRANSACTION)
        .where(TRANSACTION.ID.eq(transaction.id))
        .fetchOne()
        ?: return

    if (transaction.status != Transactionstatus.PENDING) {
        return
    }

    val guildId = transaction.guildid
    val channelId = transaction.channelid
    val messageId = transaction.messageid

    // Validações rápidas
    if (guildId == null || channelId == null || messageId == null) {
        setTransactionExpired(dsl, transaction.id!!)
        return
    }

    val guild = jda.getGuildById(guildId)
        ?: return setTransactionExpired(dsl, transaction.id!!)

    val channel = guild.getTextChannelById(channelId)
        ?: return setTransactionExpired(dsl, transaction.id!!)

    val locale = LanguageUtils.transform(guild.locale.locale)

    val traduction = when (locale) {
        "ptbr" -> "${Icon.static.get("Eris_cry")} | Os participantes dessa transação demoraram demais para responder! por isso ela foi expirada!"
        "eses" -> "${Icon.static.get("Eris_cry")} | ¡Los participantes de esta transacción tardaron demasiado en responder! ¡Por eso ha expirado!"
        else -> "${Icon.static.get("Eris_cry")} | The participants in this transaction took too long to respond! That's why it expired!"
    }

    channel.retrieveMessageById(messageId).queue(
        { message ->
            // sucesso → edita mensagem e expira no DB
            setTransactionExpired(dsl, transaction.id!!)
            message.editMessageComponents(
                ComponentBuilder.ContainerBuilder.create()
                    .addText(traduction)
                    .withColor(Colors.DANGER)
                    .build()
            ).queue()
        },
        { error ->
            setTransactionExpired(dsl, transaction.id!!)
        }
    )
}

private fun setTransactionExpired(tx: DSLContext, transactionId: Int) {
    tx.update(TRANSACTION)
        .set(TRANSACTION.STATUS, Transactionstatus.EXPIRED)
        .where(TRANSACTION.ID.eq(transactionId))
}