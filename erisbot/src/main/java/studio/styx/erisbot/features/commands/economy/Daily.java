package studio.styx.erisbot.features.commands.economy;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.jooq.DSLContext;
import org.jooq.TransactionalRunnable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import studio.styx.erisbot.core.Colors;
import studio.styx.erisbot.core.CommandInterface;
import studio.styx.erisbot.jooq.tables.Cooldown;
import studio.styx.erisbot.jooq.tables.records.CooldownRecord;
import studio.styx.erisbot.jooq.tables.records.UserRecord;
import studio.styx.erisbot.utils.ComponentBuilder;
import studio.styx.erisbot.utils.Utils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static studio.styx.erisbot.jooq.tables.Cooldown.COOLDOWN;
import static studio.styx.erisbot.jooq.tables.User.USER;

@Component
public class Daily implements CommandInterface {

    @Autowired
    private DSLContext dsl;

    private static final BigDecimal BASE_AMOUNT = BigDecimal.valueOf(500);
    private static final int[] RANDOM_BONUS = {100, 150, 200, 250, 300, 400, 500}; // bônus aleatório

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String userId = event.getUser().getId();

        // === TUDO EM TRANSAÇÃO (seguro contra race condition) ===
        dsl.transaction((TransactionalRunnable) config -> {
            DSLContext tx = config.dsl();

            // 1. Busca ou cria usuário + cooldown em 2 queries otimizadas
            UserRecord user = Utils.getOrCreateUser(tx, userId);
            CooldownRecord cooldown = getCooldown(tx, userId);

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime nextDaily = now.plusDays(1);

            // 2. Verifica cooldown
            if (cooldown != null && cooldown.getWillendin().isAfter(now)) {
                ComponentBuilder.ContainerBuilder.create()
                        .addText("Você já pegou seu daily hoje! Volte em: <t:%d:R>".formatted(cooldown.getWillendin().toEpochSecond(java.time.ZoneOffset.UTC)))
                        .withColor(Colors.DANGER)
                        .disableMentions()
                        .setEphemeral(true)
                        .reply(event);
                return;
            }
            BigDecimal bonus = BigDecimal.valueOf(Utils.getRandomInt(30, 80));

            // 4. Dá o dinheiro
            user.setMoney(user.getMoney().add(bonus));
            user.setUpdatedat(now);
            user.store();

            // 5. Atualiza cooldown + streak
            upsertCooldown(tx, userId, nextDaily);

            // 6. Resposta linda
            String message = "Você obeteve seu daily diário no valor de: **%s** stx!".formatted(
                    Utils.formatMoney(bonus)
            );

            ComponentBuilder.ContainerBuilder.create()
                    .addText(message)
                    .withColor(Colors.FUCHSIA)
                    .disableMentions()
                    .setEphemeral(false)
                    .reply(event);
        });
    }

    // === BUSCA COOLDOWN ===
    private CooldownRecord getCooldown(DSLContext tx, String userId) {
        return tx.selectFrom(COOLDOWN)
                .where(COOLDOWN.USERID.eq(userId).and(COOLDOWN.NAME.eq("daily")))
                .fetchOne();
    }

    // === UPSERT COOLDOWN + STREAK ===
    private void upsertCooldown(DSLContext tx, String userId, LocalDateTime nextDaily) {
        tx.insertInto(COOLDOWN)
                .columns(COOLDOWN.USERID, COOLDOWN.NAME, COOLDOWN.WILLENDIN)
                .values(userId, "daily", nextDaily)
                .onConflict(COOLDOWN.USERID, COOLDOWN.NAME) // ← AGORA FUNCIONA!
                .doUpdate()
                .set(COOLDOWN.WILLENDIN, nextDaily)
                .execute();
    }

    @Override
    public SlashCommandData getSlashCommandData() {
        return Commands.slash("daily", "Pegue seu bônus diário");
    }
}