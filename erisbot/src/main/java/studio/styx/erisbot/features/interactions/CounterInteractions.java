package studio.styx.erisbot.features.interactions;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.springframework.stereotype.Component;
import studio.styx.erisbot.core.Colors;
import studio.styx.erisbot.core.ResponderInterface;
import studio.styx.erisbot.utils.ComponentBuilder;
import studio.styx.erisbot.utils.Utils;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class CounterInteractions implements ResponderInterface {
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    @Override
    public void execute(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        int seconds = extractSeconds(componentId);

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
        **Contagem refeita!**
                
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
                            Button.primary("counter/retry/" + seconds, " Mais uma vez!")
                    ))
                    .withColor(Colors.FUCHSIA)
                    .disableMentions()
                    .setEphemeral(false)
                    .reply(event.getHook());
        };

        if (seconds > 2) {
            // >2s: defer + loading + responde no final
            int finalSeconds1 = seconds;
            event.deferEdit().queue(hook -> {
                ComponentBuilder.ContainerBuilder.create()
                        .addText("**Contando por %d segundo(s)...**".formatted(seconds))
                        .withColor(Colors.FUCHSIA)
                        .disableMentions()
                        .reply(event.getHook());

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
                    .editOriginal(event);
        }
    }

    @Override
    public String getCustomId() {
        return "counter/retry/:seconds";
    }

    private int extractSeconds(String customId) {
        try {
            String[] parts = customId.split("/");
            return parts.length == 3 ? Integer.parseInt(parts[2]) : 1;
        } catch (Exception e) {
            System.err.println("Erro ao extrair userId de " + customId + ": " + e.getMessage());
            return 1;
        }
    }
}
