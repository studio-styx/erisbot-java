package studio.styx.erisbot.features.commands;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.springframework.stereotype.Component;
import shared.Colors;
import shared.utils.Utils;
import studio.styx.erisbot.core.CommandInterface;
import utils.ComponentBuilder;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class Counter implements CommandInterface {

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        int seconds = event.getOption("time") != null ? event.getOption("time").getAsInt() : 1;
        seconds = Math.clamp(seconds, 1, 15);

        AtomicLong counter = new AtomicLong(0);
        long startTime = System.nanoTime();

        // Task de contagem (roda o tempo todo)
        int finalSeconds2 = seconds;
        Runnable countingTask = () -> {
            while (true) {
                long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
                if (elapsedMs >= finalSeconds2 * 1000L) break;

                // CONTA INSANAMENTE (ajuste pra mais velocidade)
                for (int i = 0; i < 2_000_000; i++) {
                    counter.incrementAndGet();
                }
            }
        };

        // Task final (responde com o resultado)
        int finalSeconds = seconds;
        Runnable sendResult = () -> {
            long finalCount = counter.get();
            double elapsedSeconds = (System.nanoTime() - startTime) / 1_000_000_000.0;
            double speed = finalCount / elapsedSeconds;

            String message = """
            **Contagem concluida!** 
            
            Tempo: **%d segundos**
            Eu contei até: **%s**
            Velocidade média: **%s por segundo**
            """.formatted(
                    finalSeconds,
                    Utils.formatNumber(finalCount),
                    Utils.formatNumber(speed)
            );

            ComponentBuilder.ContainerBuilder.create()
                    .addText(message)
                    .addRow(ActionRow.of(
                            Button.primary("counter/retry/" + finalSeconds, " Contar novamente")
                    ))
                    .withColor(Colors.FUCHSIA)
                    .disableMentions()
                    .setEphemeral(false)
                    .reply(event.getHook());
        };

        if (seconds > 2) {
            ComponentBuilder.ContainerBuilder.create()
                    .addText("**Contando por %d segundo(s)...**".formatted(seconds))
                    .withColor(Colors.FUCHSIA)
                    .disableMentions()
                    .reply(event.getHook());
            // >2s: defer + loading + responde no final
            int finalSeconds1 = seconds;
            event.deferReply().queue(hook -> {
                scheduler.submit(countingTask);
                scheduler.schedule(sendResult, finalSeconds1, TimeUnit.SECONDS);
            });
        } else {
            // ≤2s: responde imediatamente + conta em background
            scheduler.submit(countingTask);
            scheduler.schedule(sendResult, seconds, TimeUnit.SECONDS);

            // Resposta inicial imediata
            ComponentBuilder.ContainerBuilder.create()
                    .addText("**Contando por %d segundo(s)...**".formatted(seconds))
                    .withColor(Colors.FUCHSIA)
                    .disableMentions()
                    .reply(event);
        }
    }

    @Override
    public SlashCommandData getSlashCommandData() {
        OptionData timeOption = new OptionData(OptionType.INTEGER, "time", "⏱️ ✦ Time in seconds (1-15)", false)
                .setMinValue(1)
                .setMaxValue(15)
                .setNameLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "tempo")
                .setDescriptionLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "⏱️ ✦ Tempo em segundos (1-15)")
                .setNameLocalization(DiscordLocale.SPANISH, "tiempo")
                .setDescriptionLocalization(DiscordLocale.SPANISH, "⏱️ ✦ Tiempo en segundos (1-15)")
                .setNameLocalization(DiscordLocale.ENGLISH_US, "time")
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US, "⏱️ ✦ Time in seconds (1-15)")
                .setNameLocalization(DiscordLocale.SPANISH_LATAM, "tiempo")
                .setDescriptionLocalization(DiscordLocale.SPANISH_LATAM, "⏱️ ✦ Tiempo en segundos (1-15)");

        return Commands.slash("counter", "🔢 ✦ Make me count for you!")
                .addOptions(timeOption)
                .setNameLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "contador")
                .setDescriptionLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "🔢 ✦ Faça eu contar até o máximo possivel")
                .setNameLocalization(DiscordLocale.SPANISH, "contador")
                .setNameLocalization(DiscordLocale.SPANISH_LATAM, "contador")
                .setDescriptionLocalization(DiscordLocale.SPANISH, "🔢 ✦ Hazme contar hasta el máximo posible")
                .setDescriptionLocalization(DiscordLocale.SPANISH_LATAM, "🔢 ✦ Hazme contar hasta el máximo posible")
                .setNameLocalization(DiscordLocale.ENGLISH_US, "counter")
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US, "🔢 ✦ Make me count for you!");

    }
}