package services.gemini

import com.google.genai.Client
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.GenerateContentResponse
import shared.utils.Env

class GeminiRequest {
    private val client: Client = Client.builder()
        .apiKey(Env.get("GEMINI_API_KEY").toString())
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