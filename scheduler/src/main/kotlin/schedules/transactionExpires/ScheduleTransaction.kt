package schedules.transactionExpires

import net.dv8tion.jda.api.JDA
import org.jooq.DSLContext
import studio.styx.erisbot.generated.tables.records.TransactionRecord
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

fun scheduleTransaction(transaction: TransactionRecord, jda: JDA, dsl: DSLContext) {
    val now = LocalDateTime.now()
    val expiresAt = transaction.expiresat

    val delay = Duration.between(now, expiresAt).toMillis()

    // Se já expirou, processa direto
    if (delay <= 0) {
        expireTransaction(transaction, jda, dsl)
        return
    }

    TransactionScheduler.executor.schedule(
        {
            expireTransaction(transaction, jda, dsl)
        },
        delay,
        TimeUnit.MILLISECONDS
    )
}
