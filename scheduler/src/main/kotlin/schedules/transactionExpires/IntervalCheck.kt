package schedules.transactionExpires

import net.dv8tion.jda.api.JDA
import org.jooq.DSLContext
import studio.styx.erisbot.generated.enums.Transactionstatus
import studio.styx.erisbot.generated.tables.references.TRANSACTION
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

fun checkAndSchedule(tx: DSLContext, jda: JDA) {
    val now = LocalDateTime.now();

    val transactionsPendings = tx.selectFrom(TRANSACTION)
        .where(TRANSACTION.STATUS.eq(Transactionstatus.PENDING))
        .and(TRANSACTION.EXPIRESAT.lt(now.plusMinutes(20)))
        .fetch()

    for (transaction in transactionsPendings) {
        scheduleTransaction(transaction, jda, tx)
    }
}

fun startIntervalCheck(tx: DSLContext, jda: JDA) {
    TransactionScheduler.executor.scheduleAtFixedRate(
        {
            try {
                checkAndSchedule(tx, jda)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        },
        0,              // delay inicial (0 = começa imediatamente)
        5,              // intervalo
        TimeUnit.MINUTES
    )
}
