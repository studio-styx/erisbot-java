package studio.styx.erisbot.functions.helpers.wordle

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import net.dv8tion.jda.api.interactions.DiscordLocale
import studio.styx.erisbot.core.dtos.wordle.WordleFileDto
import studio.styx.erisbot.core.dtos.wordle.WordleWord
import java.io.FileNotFoundException

class GetWordsHelper(
    private val language: DiscordLocale,
    private val length: Int? = null
) {
    private val mapper = jacksonObjectMapper()

    private fun readFile(): WordleFileDto {
        // Carrega o arquivo da pasta resources
        val inputStream = this::class.java.getResourceAsStream("/wordle/words.json")
            ?: throw FileNotFoundException("Arquivo resources/wordle/words.json não encontrado!")

        // O Jackson lê o stream e converte direto para as Data Classes
        return inputStream.use {
            mapper.readValue<WordleFileDto>(it)
        }
    }

    fun getWords(): List<WordleWord> {
        val fileData = readFile()

        // Seleciona a lista baseada no idioma do Discord
        val rawList = when (language) {
            DiscordLocale.PORTUGUESE_BRAZILIAN -> fileData.ptbr.words
            DiscordLocale.SPANISH -> fileData.es.words
            else -> fileData.en.words
        }

        // Se length for nulo, retorna tudo. Se tiver valor, filtra pelo tamanho.
        return if (length != null) {
            rawList.filter { it.length == length }
        } else {
            rawList
        }
    }
}