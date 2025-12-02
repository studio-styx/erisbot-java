package emojis

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.cdimascio.dotenv.Dotenv
import java.nio.file.Paths

data class EmojiData(
    val static: Map<String, String> = emptyMap(),
    val animated: Map<String, String> = emptyMap()
)

object EmojiLoader {
    private val mapper = jacksonObjectMapper()

    private val env = try {
        Dotenv.load()
    } catch (e: Exception) {
        null
    }

    private val environment = env?.get("ENV")?.lowercase() ?: "production"

    private val emojiFile = when (environment) {
        "dev", "development", "true" -> Paths.get("emojis.dev.json").toFile()
        else -> Paths.get("emojis.json").toFile()
    }

    val emojis: EmojiData by lazy {
        if (emojiFile.exists()) {
            try {
                mapper.readValue(emojiFile)
            } catch (e: Exception) {
                println("⚠️ Erro ao ler JSON de emojis: ${e.message}")
                EmojiData()
            }
        } else {
            println("⚠️ Arquivo ${emojiFile.name} não encontrado! Retornando vazio.")
            EmojiData()
        }
    }
}