package database.utils

import org.jooq.DSLContext
import studio.styx.erisbot.generated.tables.records.LogRecord
import studio.styx.erisbot.generated.tables.references.LOG
import java.time.LocalDateTime

enum class LogTypes {
    INFO,
    WARN,
    ERROR,
    DEBUG
}

class LogManage {
    class CreateLog private constructor() {
        private var message: String? = null;
        private var userId: String? = null;
        private var level: Int = 1;
        private var type: LogTypes = LogTypes.INFO;
        private var tags: List<String> = listOf()

        fun setMessage(message: String) = apply { this.message = message }
        fun setUserId(userId: String) = apply { this.userId = userId }
        fun setLevel(level: Int) = apply { this.level = level }
        fun setType(type: LogTypes) = apply { this.type = type }
        fun setTags(tags: List<String>) = apply { this.tags = tags }

        fun insert(tx: DSLContext): LogRecord {
            val now = LocalDateTime.now()
            
            return tx.insertInto(LOG)
                .set(LOG.USERID, userId)
                .set(LOG.MESSAGE, message)
                .set(LOG.LEVEL, level)
                .set(LOG.TIMESTAMP, now)
                .set(LOG.TAGS, tags.toTypedArray())
                .returning()
                .fetchOne()!!

        }

        companion object {
            @JvmStatic
            fun create(): CreateLog = CreateLog()
        }
    }
}