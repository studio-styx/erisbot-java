package studio.styx.erisbot.features.commands.economy.cassino;

import database.utils.DatabaseUtils;
import database.utils.LogManage;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import shared.Colors;
import studio.styx.erisbot.core.CommandInterface;
import studio.styx.erisbot.generated.enums.Rarity;
import studio.styx.erisbot.generated.tables.records.PetRecord;
import studio.styx.erisbot.generated.tables.records.PetskillRecord;
import studio.styx.erisbot.generated.tables.records.UserRecord;
import studio.styx.erisbot.generated.tables.records.UserpetskillRecord;
import studio.styx.erisbot.generated.tables.references.TablesKt;
import translates.TranslatesObjects;
import translates.commands.economy.cassino.SlotMachineTranslateInterface;
import utils.ComponentBuilder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class Slots implements CommandInterface {
    @Autowired
    private DSLContext dsl;

    private final Random random = new Random();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    // Bônus por raridade
    private final Map<Rarity, Double> rarityBonus = Map.of(
            Rarity.COMUM, 0.05,
            Rarity.UNCOMUM, 0.1,
            Rarity.RARE, 0.15,
            Rarity.EPIC, 0.2,
            Rarity.LEGENDARY, 0.25
    );

    private final String[] slots = {"🍒", "🍊", "🍋", "🍉", "🍇", "🍓", "🍎", "🍐"};

    @Override
    public SlashCommandData getSlashCommandData() {
        return Commands.slash("slots", "Jogue na slot machine")
                .addOption(OptionType.NUMBER, "amount", "Quantidade para apostar", true);
    }


    @Override
    public void execute(SlashCommandInteractionEvent event) {
        event.deferReply().queue(hook -> dsl.transaction(config -> {
            DSLContext tx = config.dsl();
            String userId = event.getUser().getId();

            // Obtém o amount da opção
            double amount = event.getOption("amount").getAsDouble();

            UserRecord userData = DatabaseUtils.getOrCreateUser(tx, userId);
            SlotMachineTranslateInterface t = TranslatesObjects.getSlotsMachine(event.getUserLocale().getLocale());

            // Verifica se tem dinheiro suficiente
            if (userData.getMoney().doubleValue() < 25) {
                hook.editOriginalComponents(
                        ComponentBuilder.ContainerBuilder.create()
                                .addText(t.getNotEnoughMoney())
                                .withColor(Colors.DANGER)
                                .build()
                ).useComponentsV2().queue();
                return;
            }

            // Ajusta o amount se for maior que o dinheiro disponível
            if (userData.getMoney().doubleValue() < amount) {
                amount = userData.getMoney().doubleValue();
            }

            // Busca o pet ativo e suas skills
            PetRecord activePet = getActivePetWithSkills(tx, userId);

            // Encontra a skill slots_luck
            UserpetskillRecord slotsLuckySkill = findSlotsLuckSkill(activePet, tx);

            // Chance base SEM pet
            double baseChance = 0.15; // 15% base

            // Calcula chance total
            double totalChance = baseChance;

            if (slotsLuckySkill != null && activePet != null) {
                // Adiciona: bônus da raridade + bônus do nível da skill
                double rarityBonusValue = rarityBonus.getOrDefault(activePet.getRarity(), 0.0);
                double skillBonus = slotsLuckySkill.getLevel() * 0.05;
                totalChance += rarityBonusValue + skillBonus;
            }

            double finalChance = Math.min(totalChance, 0.6);

            // Determina se é jackpot forçado
            boolean isForcedJackpot = random.nextDouble() < finalChance;
            String slot1, slot2, slot3;

            if (isForcedJackpot) {
                String winningSymbol = slots[random.nextInt(slots.length)];
                slot1 = slot2 = slot3 = winningSymbol;
            } else {
                slot1 = slots[random.nextInt(slots.length)];
                slot2 = slots[random.nextInt(slots.length)];
                slot3 = slots[random.nextInt(slots.length)];
            }

            boolean isWin = slot1.equals(slot2) && slot2.equals(slot3);

            // Embed inicial
            Container initialContainer = ComponentBuilder.ContainerBuilder.create()
                    .addText(t.getTitle())
                    .addDivider(false)
                    .addText(t.slot1(slot1))
                    .withColor(Colors.PRIMARY)
                    .build();

            double finalAmount = amount;
            hook.editOriginalComponents(initialContainer).useComponentsV2().queue(success -> {
                // Primeira animação após 2 segundos
                scheduler.schedule(() -> {
                    Container secondContainer = ComponentBuilder.ContainerBuilder.create()
                            .addText(t.getTitle())
                            .addDivider(false)
                            .addText(t.slot2(slot1, slot2))
                            .withColor(Colors.PRIMARY)
                            .build();

                    hook.editOriginalComponents(secondContainer).useComponentsV2().queue(secondSuccess -> {
                        // Segunda animação após mais 2 segundos
                        scheduler.schedule(() -> {
                            double winAmount = finalAmount * 0.6;

                            // Atualiza o saldo do usuário
                            updateUserBalance(tx, userId, isWin, isWin ? winAmount : finalAmount);

                            // Registra o log
                            registerLog(event, isWin, winAmount, finalAmount);

                            // Embed final
                            String description = isWin
                                    ? t.winMessage(slot1, slot2, slot3, winAmount)
                                    : t.loseMessage(slot1, slot2, slot3, finalAmount);

                            Container finalContainer = ComponentBuilder.ContainerBuilder.create()
                                    .addText(t.getTitle())
                                    .addDivider(false)
                                    .addText(description)
                                    .withColor(isWin ? Colors.SUCCESS : Colors.DANGER)
                                    .build();

                            hook.editOriginalComponents(finalContainer).useComponentsV2().queue();
                        }, 2, TimeUnit.SECONDS);
                    });
                }, 2, TimeUnit.SECONDS);
            });
        }));
    }

    private PetRecord getActivePetWithSkills(DSLContext tx, String userId) {
        return tx.select(TablesKt.getPET().asterisk())
                .from(TablesKt.getPET())
                .join(TablesKt.getUSER()).on(TablesKt.getUSER().getACTIVEPETID().eq(TablesKt.getPET().getID()))
                .where(TablesKt.getUSER().getID().eq(userId))
                .fetchOneInto(PetRecord.class);
    }

    private UserpetskillRecord findSlotsLuckSkill(PetRecord activePet, DSLContext tx) {
        if (activePet == null) return null;

        return tx.select(TablesKt.getUSERPETSKILL().asterisk())
                .from(TablesKt.getPETSKILL())
                .join(TablesKt.getUSERPETSKILL()).on(TablesKt.getUSERPETSKILL().getSKILLID().eq(TablesKt.getPETSKILL().getID()))
                .where(TablesKt.getUSERPETSKILL().getUSERPETID().eq(activePet.getId())
                        .and(TablesKt.getPETSKILL().getNAME().eq("slots_luck")))
                .fetchOneInto(UserpetskillRecord.class);
    }

    private void updateUserBalance(DSLContext tx, String userId, boolean isWin, double amount) {
        if (isWin) {
            tx.update(TablesKt.getUSER())
                    .set(TablesKt.getUSER().getMONEY(), TablesKt.getUSER().getMONEY().add(BigDecimal.valueOf(amount)))
                    .where(TablesKt.getUSER().getID().eq(userId))
                    .execute();
        } else {
            tx.update(TablesKt.getUSER())
                    .set(TablesKt.getUSER().getMONEY(), TablesKt.getUSER().getMONEY().subtract(BigDecimal.valueOf(amount)))
                    .where(TablesKt.getUSER().getID().eq(userId))
                    .execute();
        }
    }

    private void registerLog(SlashCommandInteractionEvent event, boolean isWin, double winAmount, double betAmount) {
        String logMessage = String.format("Slots: %s | %s | Bet: %.2f | Win: %.2f",
                event.getUser().getGlobalName(),
                isWin ? "WIN" : "LOSE",
                betAmount,
                isWin ? winAmount : 0.0);

        LogManage.CreateLog.create()
                .setUserId(event.getUser().getId())
                .setMessage(logMessage)
                .setLevel(3)
                .setTags(List.of("Slots", "cassino", "economy", isWin ? "win" : "lose"));
    }
}
