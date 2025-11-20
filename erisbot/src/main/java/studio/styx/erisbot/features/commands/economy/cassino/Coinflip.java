package studio.styx.erisbot.features.commands.economy.cassino;

import database.utils.DatabaseUtils;
import database.utils.LogManage;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import shared.Colors;
import studio.styx.erisbot.core.CommandInterface;
import studio.styx.erisbot.generated.enums.Rarity;
import studio.styx.erisbot.generated.tables.records.UserRecord;
import studio.styx.erisbot.generated.tables.records.UserpetRecord;
import studio.styx.erisbot.generated.tables.references.TablesKt;
import translates.TranslatesObjects;
import translates.commands.economy.cassino.CoinflipCommandInterface;
import translates.commands.economy.cassino.CoinflipSide;
import utils.ContainerRes;

import java.util.List;
import java.util.Map;

@Component
public class Coinflip implements CommandInterface {
    @Autowired
    private DSLContext dsl;

    private ContainerRes res = new ContainerRes();

    @Override
    public SlashCommandData getSlashCommandData() {
        OptionData coinflipSide = new OptionData(OptionType.STRING, "side", "side of coin", true)
                .addChoice("HEADS", "HEADS")
                .addChoice("TAILS", "TAILS");

        OptionData amount = new OptionData(OptionType.NUMBER, "amount", "amount to bet", true)
                .setMinValue(20);

        return Commands.slash("coinflip", "bet in coin side")
                .addOptions(coinflipSide, amount);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        event.deferReply().queue(hook -> dsl.transaction(config -> {
            DSLContext tx = config.dsl();
            String userId = event.getUser().getId();
            CoinflipSide sideStr = CoinflipSide.valueOf(event.getOption("side").getAsString().toUpperCase());
            double amount = event.getOption("amount").getAsDouble();

            CoinflipCommandInterface t = TranslatesObjects.getCoinflip(event.getUserLocale().getLocale());

            var record = tx.select(
                            TablesKt.getUSER().getMONEY(),
                            TablesKt.getUSERPET().getID(),
                            TablesKt.getPET().getRARITY(),
                            TablesKt.getPETSKILL().getNAME().as("skill_name"),
                            TablesKt.getUSERPETSKILL().getLEVEL().as("skill_level")
                    )
                    .from(TablesKt.getUSER())
                    .leftJoin(TablesKt.getUSERPET()).on(
                            TablesKt.getUSER().getACTIVEPETID().eq(TablesKt.getUSERPET().getID())
                                    .and(TablesKt.getUSERPET().getUSERID().eq(userId))
                    )
                    .leftJoin(TablesKt.getPET()).on(
                            TablesKt.getUSERPET().getPETID().eq(TablesKt.getPET().getID())
                    )
                    .leftJoin(TablesKt.getUSERPETSKILL()).on(
                            TablesKt.getUSERPET().getID().eq(TablesKt.getUSERPETSKILL().getUSERPETID())
                    )
                    .leftJoin(TablesKt.getPETSKILL()).on(
                            TablesKt.getUSERPETSKILL().getSKILLID().eq(TablesKt.getPETSKILL().getID())
                    )
                    .where(TablesKt.getUSER().getID().eq(userId))
                    .fetch();

            if (record.isEmpty()) {
                res.setColor(Colors.DANGER).setText(t.notEnoughMoney());
                hook.editOriginalComponents(res.build()).useComponentsV2().queue();
                return;
            }

            double userMoney = record.get(0).get(TablesKt.getUSER().getMONEY()).doubleValue();
            if (userMoney < 15) {
                res.setColor(Colors.DANGER).setText(t.notEnoughMoney());
                hook.editOriginalComponents(res.build()).useComponentsV2().queue();
                return;
            }

            if (userMoney < amount) {
                amount = userMoney;
            }

            // Aggregate skills and pet rarity from the record
            double baseChance = 0.5;
            double totalChance = baseChance;
            double baseAmountBonus = 0.2;
            double totalAmountBonus = baseAmountBonus;

            // Define rarity bonuses
            Map<String, Double> rarityLuckBonus = Map.of(
                    "COMUM", 0.02,
                    "UNCOMUM", 0.04,
                    "RARE", 0.06,
                    "EPIC", 0.08,
                    "LEGENDARY", 0.10
            );

            Map<String, Double> rarityAmountBonus = Map.of(
                    "COMUM", 0.4,
                    "UNCOMUM", 0.6,
                    "RARE", 0.8,
                    "EPIC", 1.2,
                    "LEGENDARY", 1.5
            );

            Rarity petRarity = null;
            int coinflipLuckLevel = 0;
            int coinflipBonusLevel = 0;

            for (var r : record) {
                if (petRarity == null) {
                    Rarity rarity = r.get(TablesKt.getPET().getRARITY());
                    if (rarity != null) {
                        petRarity = rarity;
                    }
                }

                String skillName = r.get("skill_name", String.class);
                Integer skillLevel = r.get("skill_level", Integer.class);

                if (skillName != null && skillLevel != null) {
                    if ("coinflip_luck".equals(skillName)) {
                        coinflipLuckLevel = skillLevel;
                    }
                    if ("coinflip_bonus".equals(skillName)) {
                        coinflipBonusLevel = skillLevel;
                    }
                }
            }

            if (petRarity != null) {
                totalChance += rarityLuckBonus.getOrDefault(petRarity.toString(), 0.0) + (coinflipLuckLevel * 0.05);
                totalAmountBonus += rarityAmountBonus.getOrDefault(petRarity.toString(), 0.0) + (coinflipBonusLevel * 0.05);
            }

            totalChance = Math.min(totalChance, 0.7);
            totalAmountBonus = Math.min(totalAmountBonus, 2.5);

            boolean isHeads = Math.random() < totalChance;

            // CORREÇÃO: Compare o enum diretamente, não com strings
            boolean userWins = (isHeads && sideStr == CoinflipSide.HEADS) || (!isHeads && sideStr == CoinflipSide.TAILS);

            if (userWins) {
                double wonValue = amount * totalAmountBonus;

                tx.update(TablesKt.getUSER())
                        .set(TablesKt.getUSER().getMONEY(), TablesKt.getUSER().getMONEY().plus(wonValue))
                        .where(TablesKt.getUSER().getID().eq(userId))
                        .execute();

                hook.editOriginalComponents(res.setColor(Colors.SUCCESS).setText(t.won(sideStr, wonValue)).build()).useComponentsV2().queue();

                LogManage.CreateLog.create()
                        .setUserId(event.getUser().getId())
                        .setMessage(t.logWon(sideStr, wonValue))
                        .setLevel(3)
                        .setTags(List.of("coinflip", "economy", "win", sideStr.toString()))
                        .insert(tx);
            } else {
                tx.update(TablesKt.getUSER())
                        .set(TablesKt.getUSER().getMONEY(), TablesKt.getUSER().getMONEY().minus(amount))
                        .where(TablesKt.getUSER().getID().eq(userId))
                        .execute();

                hook.editOriginalComponents(res.setColor(Colors.DANGER).setText(t.lose(sideStr, amount)).build()).useComponentsV2().queue();

                LogManage.CreateLog.create()
                        .setUserId(event.getUser().getId())
                        .setMessage(t.logLose(sideStr, amount))
                        .setLevel(3)
                        .setTags(List.of("coinflip", "economy", "lose", sideStr.toString()))
                        .insert(tx);
            }
        }));
    }
}
