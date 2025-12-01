package studio.styx.erisbot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import schedules.transactionExpires.IntervalCheckKt;
import server.ApplicationKt;
import studio.styx.erisbot.core.CommandManager;
import studio.styx.erisbot.core.init.InitCommandsAndCache;
import studio.styx.erisbot.core.interfaces.CommandInterface;

import java.util.List;
import java.util.stream.Collectors;

@SpringBootApplication
public class Main implements CommandLineRunner {

    @Autowired
    private DSLContext dsl;

    @Autowired
    private JDA jda; // Injetado já construído e com listeners registrados pelo JdaConfiguration.kt

    @Autowired
    private CommandManager commandManager;

    @Autowired
    private List<CommandInterface> commands; // Spring injeta os comandos automaticamente

    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        // === INICIA O SERVIDOR API ===
        ApplicationKt.main(jda, dsl);

        // === INJETA O DSL NOS COMANDOS ===
        commandManager.injectDslIntoCommands();

        // === REGISTRA COMANDOS NO DISCORD ===
        registerCommandsToDiscord(jda, commands);

        // === CARREGA AGENDAMENTOS ===
        IntervalCheckKt.startIntervalCheck(dsl, jda);
    }

    private void registerCommandsToDiscord(JDA jda, List<CommandInterface> commands) {
        if (commands.isEmpty()) {
            System.out.println("Nenhum comando encontrado para registrar.");
            return;
        }

        List<SlashCommandData> commandDataList = commands.stream()
                .map(CommandInterface::getSlashCommandData)
                .collect(Collectors.toList());

        System.out.println("Registrando " + commandDataList.size() + " comandos no Discord...");

        jda.updateCommands().addCommands(commandDataList).queue(
                success -> {
                    System.out.println("Comandos registrados com sucesso no Discord!");
                    // Atualiza DB e Cache
                    jda.retrieveCommands().queue(discordCommands ->
                            new InitCommandsAndCache().registerCommandsInDb(dsl, discordCommands)
                    );
                },
                error -> System.err.println("Erro ao registrar comandos: " + error.getMessage())
        );
    }
}