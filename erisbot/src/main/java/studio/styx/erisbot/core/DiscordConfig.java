package studio.styx.erisbot.core;
import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DiscordConfig {

    private final String token;

    public DiscordConfig() {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        this.token = dotenv.get("DISCORD_TOKEN");

        if (token == null || token.isEmpty()) {
            throw new IllegalStateException("DISCORD_TOKEN não encontrado no .env");
        }
    }

    public String getToken() {
        return token;
    }
}