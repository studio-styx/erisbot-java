package studio.styx.erisbot.core.dtos.wordle

data class WordleFileDto (
    val ptbr: WordleLanguageProperties,
    val en: WordleLanguageProperties,
    val es: WordleLanguageProperties
)

data class WordleLanguageProperties(
    val words: List<WordleWord>
)