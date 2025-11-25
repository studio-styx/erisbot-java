package services.gemini

import com.google.genai.Client
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.GenerateContentResponse

class GeminiRequest {
    private val client: Client = Client.builder()
        .apiKey(System.getenv("GEMINI_API_KEY"))
        .build()

    fun request(prompt: String): GenerateContentResponse? {
        return client.models.generateContent(
            "gemini-2.5-flash",
            prompt,
            null
        )
    }

    fun request(prompt: String, config: GenerateContentConfig?): GenerateContentResponse? {
        return client.models.generateContent(
            "gemini-2.5-flash",
            prompt,
            config
        )
    }
}