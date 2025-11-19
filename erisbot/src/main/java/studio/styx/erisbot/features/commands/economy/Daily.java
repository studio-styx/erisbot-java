package studio.styx.erisbot.features.commands.economy;

import database.utils.DatabaseUtils;
import database.utils.LogManage;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import shared.Cache;
import shared.Colors;
import shared.utils.Utils;
import studio.styx.erisbot.core.CommandInterface;
import studio.styx.erisbot.generated.enums.Gender;
import studio.styx.erisbot.generated.enums.Rarity;
import studio.styx.erisbot.generated.tables.records.CooldownRecord;
import studio.styx.erisbot.generated.tables.records.UserRecord;
import studio.styx.erisbot.generated.tables.records.UserpetRecord;
import studio.styx.erisbot.generated.tables.references.TablesKt;
import translates.TranslatesObjects;
import translates.commands.economy.general.DailyGender;
import translates.commands.economy.general.DailyTranslateInterface;
import translates.commands.economy.general.PetDailyAbilities;
import utils.ComponentBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class Daily implements CommandInterface {
    private static final Logger log = LoggerFactory.getLogger(Daily.class);

    @Autowired
    private DSLContext dsl;

    private static final Map<Rarity, Double> RARITY_BONUS = Map.of(
            Rarity.COMUM, 1.0,
            Rarity.UNCOMUM, 1.2,
            Rarity.RARE, 1.5,
            Rarity.EPIC, 2.0,
            Rarity.LEGENDARY, 3.0
    );

    private static final Map<Rarity, Double> RARITY_COOLDOWN = Map.of(
            Rarity.COMUM, 1.0,
            Rarity.UNCOMUM, 0.9,
            Rarity.RARE, 0.8,
            Rarity.EPIC, 0.7,
            Rarity.LEGENDARY, 0.5
    );

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String userId = event.getUser().getId();
        DailyTranslateInterface t = TranslatesObjects.getDaily(event.getUserLocale().getLocale());

        String cacheKey = "user:daily:manyAttempts:" + userId;
        Integer attempts = Cache.get(cacheKey);
        if (attempts != null) {
            String message = t.manyAttempts(attempts);
            if (message == null) return;
            ComponentBuilder.ContainerBuilder.create()
                    .addText(message)
                    .setEphemeral(true)
                    .withColor(Colors.DANGER)
                    .reply(event);
            Cache.set(cacheKey, attempts + 1, 60 * 2);
            return;
        }

        dsl.transaction(config -> {
            DSLContext tx = config.dsl();
            LocalDateTime now = LocalDateTime.now();

            UserRecord user = DatabaseUtils.getOrCreateUser(tx, userId);
            CooldownRecord cooldown = getCooldown(tx, userId);

            if (cooldown != null && cooldown.getWillendin() != null && cooldown.getWillendin().isAfter(now)) {
                Cache.set(cacheKey, 1); // inicia contador
                ComponentBuilder.ContainerBuilder.create()
                        .addText(t.cooldown(cooldown.getWillendin().toEpochSecond(ZoneOffset.UTC)))
                        .withColor(Colors.DANGER)
                        .disableMentions()
                        .setEphemeral(true)
                        .reply(event);
               return;
            }

            Integer activePetId = user.getActivepetid();
            user.setActivepetid(activePetId);
            ActivePetData petData = getActivePetWithSkills(tx, activePetId);

            // === CÁLCULO DO BÔNUS ===
            int baseMax = 100;
            int dailyValue = Utils.getRandomInt(30, baseMax);
            LocalDateTime nextDaily = now.plusHours(24);
            int hours = 24;

            if (petData != null && petData.rarity != null) {
                double rarityMult = RARITY_BONUS.getOrDefault(petData.rarity, 1.0);
                int bonusLevel = petData.dailyBonusLevel != null ? petData.dailyBonusLevel : 0;
                double levelMult = 1 + (bonusLevel * 0.05);
                int maxDailyValue = (int) Math.floor(baseMax * rarityMult * levelMult);
                dailyValue = Utils.getRandomInt(30, maxDailyValue);

                int cooldownLevel = petData.cooldownReductionLevel != null ? petData.cooldownReductionLevel : 0;
                double baseCooldownMult = RARITY_COOLDOWN.getOrDefault(petData.rarity, 1.0);
                double reduction = Math.min(cooldownLevel * 0.02, 0.5);
                double finalCooldownMult = baseCooldownMult - reduction;
                hours = Math.max((int) (24 * finalCooldownMult), 1);
                nextDaily = now.plusHours(hours);
            }

            BigDecimal bonus = BigDecimal.valueOf(dailyValue);

            user.setMoney(Objects.requireNonNullElse(user.getMoney(), BigDecimal.ZERO).add(bonus));
            user.setUpdatedat(now);
            user.store();

            upsertCooldown(tx, userId, nextDaily);

            String petName = "seu pet";
            if (petData != null && petData.name != null && !petData.name.trim().isEmpty()) {
                petName = petData.name;
            }

            DailyGender gender = DailyGender.MASC;
            if (petData != null && petData.gender != null) {
                gender = petData.gender == Gender.MALE ? DailyGender.MASC : DailyGender.FEM;
            }

            boolean hasBonus = petData != null && petData.dailyBonusLevel != null && petData.dailyBonusLevel > 0;
            boolean hasCooldownReduction = petData != null && petData.cooldownReductionLevel != null && petData.cooldownReductionLevel > 0;

            PetDailyAbilities abilities = new PetDailyAbilities(hasBonus, hasCooldownReduction);

            String message;
            try {
                if (hasBonus || hasCooldownReduction) {
                    message = t.message(
                            bonus.intValue(),
                            user.getMoney().setScale(2, RoundingMode.HALF_UP).doubleValue(),
                            petName,
                            gender,
                            abilities
                    );
                } else {
                    message = t.message(
                            bonus.intValue(),
                            user.getMoney().setScale(2, RoundingMode.HALF_UP).doubleValue()
                    );
                }
            } catch (Exception e) {
                throw new RuntimeException("Tradução falhou", e);
            }

            ComponentBuilder.ContainerBuilder.create()
                    .addText(message)
                    .withColor(Colors.FUCHSIA)
                    .disableMentions()
                    .setEphemeral(false)
                    .reply(event);

            List<String> tags = new java.util.ArrayList<>(List.of("daily", "economy", "reward:" + bonus.intValue()));
            var log = LogManage.CreateLog.create()
                    .setLevel(2);

            if (petData != null && petData.name != null) {
                log.setMessage(t.log(bonus.intValue(), petData.name, gender, abilities));
                tags.add("pet:" + petData.name);
            } else {
                log.setMessage(t.log(bonus.intValue()));
            }
            log.setUserId(userId)
                    .setTags(tags)
                    .insert(tx);
        });
    }

    private CooldownRecord getCooldown(DSLContext tx, String userId) {
        return tx.selectFrom(TablesKt.getCOOLDOWN())
                .where(TablesKt.getCOOLDOWN().getUSERID().eq(userId)
                        .and(TablesKt.getCOOLDOWN().getNAME().eq("daily")))
                .fetchOne();
    }

    private static class ActivePetData {
        final UserpetRecord pet;
        final String name;
        final Gender gender;
        final Rarity rarity;
        final Integer dailyBonusLevel;
        final Integer cooldownReductionLevel;

        ActivePetData(UserpetRecord pet, String name, Gender gender, Rarity rarity,
                      Integer dailyBonusLevel, Integer cooldownReductionLevel) {
            this.pet = pet;
            this.name = name;
            this.gender = gender;
            this.rarity = rarity;
            this.dailyBonusLevel = dailyBonusLevel;
            this.cooldownReductionLevel = cooldownReductionLevel;
        }
    }

    private ActivePetData getActivePetWithSkills(DSLContext tx, Integer activePetId) {
        if (activePetId == null) return null;

        var userPet = TablesKt.getUSERPET();
        var pet = TablesKt.getPET();
        var userPetSkill = TablesKt.getUSERPETSKILL();
        var petSkill = TablesKt.getPETSKILL();

        var dailyBonusLevel = DSL.max(
                DSL.when(DSL.lower(petSkill.getNAME()).eq("daily_bonus"), userPetSkill.getLEVEL())
        ).as("daily_bonus_level");

        var dailyCooldownLevel = DSL.max(
                DSL.when(DSL.lower(petSkill.getNAME()).eq("daily_cooldown_reduction"), userPetSkill.getLEVEL())
        ).as("daily_cooldown_level");

        Record record = tx.select(
                        userPet.getID(), userPet.getUSERID(), userPet.getPETID(), userPet.getNAME(),
                        userPet.getHUNGRY(), userPet.getLIFE(), userPet.getHAPPINESS(), userPet.getENERGY(),
                        userPet.getISDEAD(), userPet.getGENDER(), userPet.getHUMOR(), userPet.getISPREGNANT(),
                        userPet.getPREGNANTENDAT(), userPet.getSPOUSEID(), userPet.getPARENT1ID(),
                        userPet.getPARENT2ID(), userPet.getCREATEDAT(), userPet.getUPDATEDAT(), userPet.getFLAGS(),
                        pet.getRARITY(),
                        dailyBonusLevel, dailyCooldownLevel
                )
                .from(userPet)
                .leftJoin(pet).on(pet.getID().eq(userPet.getPETID()))
                .leftJoin(userPetSkill).on(userPetSkill.getUSERPETID().eq(userPet.getID()))
                .leftJoin(petSkill).on(petSkill.getID().eq(userPetSkill.getSKILLID()))
                .where(userPet.getID().eq(activePetId))
                .groupBy(userPet.getID(), pet.getRARITY())
                .fetchOne();

        if (record == null) return null;

        UserpetRecord petRecord = new UserpetRecord();
        petRecord.setId(record.get(userPet.getID()));
        petRecord.setUserid(record.get(userPet.getUSERID()));
        petRecord.setPetid(record.get(userPet.getPETID()));
        petRecord.setName(record.get(userPet.getNAME()));
        petRecord.setHungry(record.get(userPet.getHUNGRY()));
        petRecord.setLife(record.get(userPet.getLIFE()));
        petRecord.setHappiness(record.get(userPet.getHAPPINESS()));
        petRecord.setEnergy(record.get(userPet.getENERGY()));
        petRecord.setIsdead(record.get(userPet.getISDEAD()));
        petRecord.setGender(record.get(userPet.getGENDER()));
        petRecord.setHumor(record.get(userPet.getHUMOR()));
        petRecord.setIspregnant(record.get(userPet.getISPREGNANT()));
        petRecord.setPregnantendat(record.get(userPet.getPREGNANTENDAT()));
        petRecord.setSpouseid(record.get(userPet.getSPOUSEID()));
        petRecord.setParent1id(record.get(userPet.getPARENT1ID()));
        petRecord.setParent2id(record.get(userPet.getPARENT2ID()));
        petRecord.setCreatedat(record.get(userPet.getCREATEDAT()));
        petRecord.setUpdatedat(record.get(userPet.getUPDATEDAT()));
        petRecord.setFlags(record.get(userPet.getFLAGS()));

        return new ActivePetData(
                petRecord,
                petRecord.getName(),
                petRecord.getGender(),
                record.get(pet.getRARITY()),
                record.get("daily_bonus_level", Integer.class),
                record.get("daily_cooldown_level", Integer.class)
        );
    }

    private void upsertCooldown(DSLContext tx, String userId, LocalDateTime nextDaily) {
        tx.insertInto(TablesKt.getCOOLDOWN())
                .columns(TablesKt.getCOOLDOWN().getUSERID(), TablesKt.getCOOLDOWN().getNAME(), TablesKt.getCOOLDOWN().getWILLENDIN())
                .values(userId, "daily", nextDaily)
                .onConflict(TablesKt.getCOOLDOWN().getUSERID(), TablesKt.getCOOLDOWN().getNAME())
                .doUpdate()
                .set(TablesKt.getCOOLDOWN().getWILLENDIN(), nextDaily)
                .execute();
    }

    @Override
    public SlashCommandData getSlashCommandData() {
        return Commands.slash("daily", "🎁 ✦ Claim your daily bonus")
                .setNameLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "diário")
                .setDescriptionLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "🎁 ✦ Pegue seu bônus diário")
                .setNameLocalization(DiscordLocale.SPANISH, "diario")
                .setDescriptionLocalization(DiscordLocale.SPANISH, "🎁 ✦ Reclama tu bono diario")
                .setNameLocalization(DiscordLocale.SPANISH_LATAM, "diario")
                .setDescriptionLocalization(DiscordLocale.SPANISH_LATAM, "🎁 ✦ Reclama tu bono diario")
                .setNameLocalization(DiscordLocale.ENGLISH_US, "daily")
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US, "🎁 ✦ Claim your daily bonus");
    }
}