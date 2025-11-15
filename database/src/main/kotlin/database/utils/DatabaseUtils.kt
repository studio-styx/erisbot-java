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

        val user = tx.insertInto(USER)
            .columns(USER.ID, USER.MONEY, USER.CREATEDAT, USER.UPDATEDAT)
            .values(userId, BigDecimal.ZERO, LocalDateTime.now(), LocalDateTime.now())
            .onConflict(USER.ID)
            .doUpdate()
            .set(USER.UPDATEDAT, LocalDateTime.now())
            .returning()
            .fetchOne()!!

        // LOG PARA DEBUG
        println("=== USER LOADED ===")
        println("userId: $userId")
        println("activePetId (getter): ${user.activepetid}")
        println("activePetId (raw): ${user.get(USER.ACTIVEPETID)}")
        println("Todos os campos: ${user.intoMap().keys}")

        return user
    }
}
