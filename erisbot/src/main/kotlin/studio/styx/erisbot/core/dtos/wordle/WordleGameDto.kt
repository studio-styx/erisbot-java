package studio.styx.erisbot.core.dtos.wordle

import java.time.LocalDateTime

data class WordleGameDto(
    val word: String,
    val attempts: MutableList<String>,
    var isOver: Boolean,
    var isWon: Boolean,
    val startedAt: LocalDateTime = LocalDateTime.now(),
    var endedAt: LocalDateTime? = null,
    var lastAttemptAt: LocalDateTime? = null,
    val guildId: String,
    val channelId: String,
    val messageId: String?,
    val userId: String,
    val maxAttempts: Int = 5
)
