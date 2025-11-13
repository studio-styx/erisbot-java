package database.utils

import org.jooq.DSLContext
import studio.styx.erisbot.generated.tables.references.USER
import java.math.BigDecimal
import java.time.LocalDateTime

object DatabaseUtils {

    @JvmStatic
    fun getOrCreateUser(
        tx: DSLContext,
        userId: String
    ): studio.styx.erisbot.generated.tables.records.UserRecord {

        return tx.insertInto(USER)
            .columns(USER.ID, USER.MONEY, USER.CREATEDAT, USER.UPDATEDAT)
            .values(
                userId,
                BigDecimal.ZERO,
                LocalDateTime.now(),
                LocalDateTime.now()
            )
            .onConflict(USER.ID)
            .doUpdate()
            .set(USER.UPDATEDAT, LocalDateTime.now())
            .returning(USER.ID, USER.MONEY, USER.CREATEDAT, USER.UPDATEDAT)
            .fetchOne()!!
    }
}