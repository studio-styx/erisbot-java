package studio.styx.erisbot.features.commands;

import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import okhttp3.MediaType;
import org.springframework.stereotype.Component;
import studio.styx.erisbot.core.CommandInterface;
import studio.styx.erisbot.utils.ComponentBuilder;
import studio.styx.erisbot.utils.EmbedReply;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

@Component
public class BotCommands implements CommandInterface {
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        var res = new EmbedReply();
        var subCommand = event.getSubcommandName();
        if (subCommand == null) {
            event.replyEmbeds(res.danger("Nenhum subcomando especificado.")).setEphemeral(true).queue();
            return;
        }

        switch (subCommand) {
            case "ping": {
                // Captura o momento inicial
                Instant dateBeforeDeferring = Instant.now();

                // Deferir a resposta (assíncrona)
                event.deferReply().queue(hook -> {
                    // Captura o momento após deferir
                    Instant dateAfterDeferring = Instant.now();

                    // Calcula a latência (em milissegundos)
                    long latency = ChronoUnit.MILLIS.between(dateBeforeDeferring, dateAfterDeferring);
                    // Calcula a latência do WebSocket do JDA
                    long gatewayPing = event.getJDA().getGatewayPing();

                    // Formata a data/hora para exibição (com fuso horário do Brasil)
                    ZonedDateTime zonedDateTime = dateBeforeDeferring.atZone(ZoneId.of("America/Sao_Paulo"));
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss z");
                    String formattedDate = zonedDateTime.format(formatter);

                    var message = "Pong!\n" +
                            "Latência da API: " + latency + "ms\n" +
                            "Latência do Gateway: " + gatewayPing + "ms\n" +
                            "Data/Hora: " + formattedDate;
                    // Responde com a latência e a data/hora
                    hook.sendMessageEmbeds(res.info(message)).queue();
                });
                break;
            }
            case "info": {
                ComponentBuilder.ContainerBuilder.create()
                        .addText("## Title")
                        .addDivider(true)
                        .addText("## Isso é a descrição")
                        .add(Section.of(
                                Button.danger("buttonTest/test1/" + event.getUser().getId(), "Botão de teste"),
                                TextDisplay.of("Section teste")
                        ))
                        .addSection(
                                Button.danger("buttonTest/test2/" + event.getUser().getId(), "Outro teste"),
                                "teste"
                        )
                        .addSection(Objects.requireNonNull(event.getUser().getAvatarUrl()), "user")
                        .withColor("#EB459E")
                        .setEphemeral(false)
                        .reply(event);
                break;
            }
            case "commands": {
                // Lista todos os comandos registrados
                StringBuilder commandList = new StringBuilder("Comandos disponíveis:\n");
                event.getJDA().retrieveCommands().queue(commands -> {
                    for (var command : commands) {
                        commandList.append("- /").append(command.getName());
                        if (!command.getSubcommands().isEmpty()) {
                            command.getSubcommands().forEach(sub -> commandList.append(" ").append(sub.getName()));
                        }
                        commandList.append(": ").append(command.getDescription()).append("\n");
                    }
                    event.reply(commandList.toString()).setEphemeral(true).queue();
                });
                break;
            }
            default:
                event.reply("Subcomando desconhecido: " + subCommand).setEphemeral(true).queue();
                break;
        }
    }

    @Override
    public SlashCommandData getSlashCommandData() {
        return Commands.slash("bot", "Comandos do bot")
                .addSubcommands(
                        new SubcommandData("ping", "Responde com o ping do bot"),
                        new SubcommandData("info", "Exibe informações do bot"),
                        new SubcommandData("help", "Mostra a ajuda do bot"),
                        new SubcommandData("commands", "Lista todos os comandos do bot")
                );
    }
}