package games.tryvia.dtos

import studio.styx.erisbot.generated.tables.records.TryviaquestionsRecord

data class TryviaParticipant(
    val id: String,
    val points: Int = 0,
    val streak: Int = 0,
    val correctAnswers: MutableList<TryviaquestionsRecord> = mutableListOf(),
    val incorrectAnswers: MutableList<TryviaquestionsRecord> = mutableListOf(),
    val createdAt: Long = System.currentTimeMillis()
)