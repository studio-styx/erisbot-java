package database.extensions

import database.dtos.log.CreateLogData
import org.jooq.DSLContext
import studio.styx.erisbot.generated.tables.records.LogRecord
import studio.styx.erisbot.generated.tables.references.LOG
import studio.styx.erisbot.generated.tables.references.USER
import java.math.BigDecimal
import java.time.LocalDateTime

fun DSLContext.createLog(data: CreateLogData): CreateLogQuery {
    return CreateLogQuery(data, dsl())
}

class CreateLogQuery(private val data: CreateLogData, private val tx: DSLContext) {
    fun insert(): LogRecord {
        return tx.insertInto(LOG)
            .set(LOG.USERID, data.userId)
            .set(LOG.MESSAGE, data.message)
            .set(LOG.LEVEL, data.level)
            .set(LOG.TYPE, data.type)
            .set(LOG.TAGS, data.tags.toTypedArray())
            .set(LOG.TIMESTAMP, LocalDateTime.now())
            .returning()
            .fetchOne() ?: throw IllegalStateException("insert failed")
    }

    fun upsertUser() {
        tx.insertInto(USER)
            .columns(USER.ID, USER.MONEY, USER.CREATEDAT, USER.UPDATEDAT)
            .values(data.userId, BigDecimal.ZERO, LocalDateTime.now(), LocalDateTime.now())
            .onConflictDoNothing()
            .execute()
    }

    fun insertWithUser(): LogRecord {
        upsertUser()
        return insert()
    }

    fun safeInsert(): LogRecord? {
        return try {
            insert()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun safeInsertWithUser(): LogRecord? {
        return try {
            upsertUser()
            insert()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}