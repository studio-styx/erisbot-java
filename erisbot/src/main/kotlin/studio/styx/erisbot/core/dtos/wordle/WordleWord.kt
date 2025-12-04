package studio.styx.erisbot.core.dtos.wordle

data class WordleWord(
    val word: String,
    val length: Int,
    val category: String,
    val language: String
)
