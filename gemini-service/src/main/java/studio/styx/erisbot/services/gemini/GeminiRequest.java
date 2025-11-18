package studio.styx.erisbot.services.gemini;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import shared.utils.Env;

import java.util.Objects;

public class GeminiRequest {
    private final Client client = Client.builder()
            .apiKey(Objects.requireNonNull(Env.get("GEMINI_API_KEY")).toString())
            .build();

    public GenerateContentResponse request(String prompt) {
        return client.models.generateContent(
                        "gemini-2.5-flash",
                        prompt,
                        null);

    }

    public GenerateContentResponse request(String prompt, GenerateContentConfig config) {
        return client.models.generateContent(
                "gemini-2.5-flash",
                prompt,
                config
        );
    }
}
