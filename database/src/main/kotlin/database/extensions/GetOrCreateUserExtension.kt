package database.extensions

import org.jooq.DSLContext
import studio.styx.erisbot.generated.tables.records.UserRecord
import studio.styx.erisbot.generated.tables.references.USER
import java.math.BigDecimal
import java.time.LocalDateTime

fun DSLContext.getOrCreateUser(userId: String): UserRecord {
    return transactionResult { config ->
        val tx = config.dsl()

        tx.insertInto(USER)
            .columns(USER.ID, USER.MONEY, USER.CREATEDAT, USER.UPDATEDAT)
            .values(userId, BigDecimal.ZERO, LocalDateTime.now(), LocalDateTime.now())
            .onConflict(USER.ID)
            .doUpdate()
            .set(USER.UPDATEDAT, LocalDateTime.now())
            .returning()
            .fetchOne()!!
    }
}