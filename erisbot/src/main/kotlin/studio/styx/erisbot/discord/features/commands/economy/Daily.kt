package studio.styx.erisbot.discord.features.commands.economy

import database.utils.DatabaseUtils.getOrCreateUser
import database.utils.LogManage.CreateLog
import dev.minn.jda.ktx.coroutines.await
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.DiscordLocale
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import org.jooq.Configuration
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import shared.Cache.get
import shared.Cache.set
import shared.Colors
import shared.utils.Utils.getRandomInt
import studio.styx.erisbot.core.interfaces.CommandInterface
import studio.styx.erisbot.generated.enums.Gender
import studio.styx.erisbot.generated.enums.Rarity
import studio.styx.erisbot.generated.tables.records.CooldownRecord
import studio.styx.erisbot.generated.tables.records.UserpetRecord
import studio.styx.erisbot.generated.tables.references.*
import translates.TranslatesObjects.getDaily
import translates.commands.economy.general.DailyGender
import translates.commands.economy.general.PetDailyAbilities
import utils.ComponentBuilder
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Map
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

@Component
class Daily : CommandInterface {
    @Autowired
    lateinit var dsl: DSLContext

    override suspend fun execute(event: SlashCommandInteractionEvent) {
        val userId = event.user.id
        val t = getDaily(event.userLocale.locale)

        // 1. CHECAGEM DE CACHE (RÁPIDA)
        val cacheKey = "user:daily:manyAttempts:$userId"
        val attempts = get<Int?>(cacheKey)
        if (attempts != null) {
            val message = t.manyAttempts(attempts) ?: return
            ComponentBuilder.ContainerBuilder.create()
                .addText(message)
                .setEphemeral(true)
                .withColor(Colors.DANGER)
                .reply(event) // Responde normal (Ephemeral)
            set(cacheKey, attempts + 1, (60 * 2).toLong())
            return
        }

        event.deferReply(false).await()

        val cooldown = dsl.selectFrom(COOLDOWN)
            .where(COOLDOWN.USERID.eq(userId).and(COOLDOWN.NAME.eq("daily")))
            .fetchOne()

        val now = LocalDateTime.now()

        if (cooldown?.willendin != null && cooldown.willendin!!.isAfter(now)) {
            set(cacheKey, 1)
            ComponentBuilder.ContainerBuilder.create()
                .addText(t.cooldown(cooldown.willendin!!.toEpochSecond(ZoneOffset.UTC)))
                .withColor(Colors.DANGER)
                .disableMentions()
                .setEphemeral(true)
                .reply(event)
            return
        }


        // 4. INICIA A TRANSAÇÃO (LENTA)
        dsl.transaction { config: Configuration? ->
            val tx = config!!.dsl()

            val user = getOrCreateUser(tx, userId)

            // Logica do Pet
            val activePetId = user.activepetid
            // Atualiza para garantir consistência
            user.activepetid = activePetId
            val petData = getActivePetWithSkills(tx, activePetId)

            // === CÁLCULO DO BÔNUS ===
            val baseMax = 100
            var dailyValue = getRandomInt(30, baseMax)
            var nextDaily = now.plusHours(24)
            var hours = 24

            if (petData?.rarity != null) {
                val rarityMult = RARITY_BONUS.getOrDefault(petData.rarity, 1.0)!!
                val bonusLevel = petData.dailyBonusLevel ?: 0
                val levelMult = 1 + (bonusLevel * 0.05)
                val maxDailyValue = floor(baseMax * rarityMult * levelMult).toInt()
                dailyValue = getRandomInt(30, maxDailyValue)

                val cooldownLevel = petData.cooldownReductionLevel ?: 0
                val baseCooldownMult = RARITY_COOLDOWN.getOrDefault(petData.rarity, 1.0)!!
                val reduction = min(cooldownLevel * 0.02, 0.5)
                val finalCooldownMult = baseCooldownMult - reduction
                hours = max((24 * finalCooldownMult).toInt(), 1)
                nextDaily = now.plusHours(hours.toLong())
            }

            val bonus = BigDecimal.valueOf(dailyValue.toLong())

            user.money = (user.money ?: BigDecimal.ZERO).add(bonus)
            user.updatedat = now
            user.store()

            upsertCooldown(tx, userId, nextDaily)

            var petName: String? = "seu pet"
            if (petData?.name != null && petData.name.trim().isNotEmpty()) {
                petName = petData.name
            }

            var gender = DailyGender.MASC
            if (petData?.gender != null) {
                gender = if (petData.gender == Gender.MALE) DailyGender.MASC else DailyGender.FEM
            }

            val hasBonus = (petData?.dailyBonusLevel ?: 0) > 0
            val hasCooldownReduction = (petData?.cooldownReductionLevel ?: 0) > 0

            val abilities = PetDailyAbilities(hasBonus, hasCooldownReduction)

            val message: String = try {
                if (hasBonus || hasCooldownReduction) {
                    t.message(
                        bonus.toInt(),
                        user.money!!.setScale(2, RoundingMode.HALF_UP).toDouble(),
                        petName!!,
                        gender,
                        abilities
                    )
                } else {
                    t.message(
                        bonus.toInt(),
                        user.money!!.setScale(2, RoundingMode.HALF_UP).toDouble()
                    )
                }
            } catch (e: Exception) {
                throw RuntimeException("Tradução falhou", e)
            }

            ComponentBuilder.ContainerBuilder.create()
                .addText(message)
                .withColor(Colors.FUCHSIA)
                .disableMentions()
                .setEphemeral(false)
                .reply(event)

            val tags = mutableListOf("daily", "economy", "reward:" + bonus.toInt())

            val log = CreateLog.create().setLevel(2)

            if (petData?.name != null) {
                log.setMessage(t.log(bonus.toInt(), petData.name, gender, abilities))
                tags.add("pet:" + petData.name) // Agora funciona!
            } else {
                log.setMessage(t.log(bonus.toInt()))
            }

            log.setUserId(userId)
                .setTags(tags)
                .insert(tx)
        }
    }

    private fun getCooldown(tx: DSLContext, userId: String?): CooldownRecord? {
        return tx.selectFrom<CooldownRecord>(COOLDOWN)
            .where(
                COOLDOWN.USERID.eq(userId)
                    .and(COOLDOWN.NAME.eq("daily"))
            )
            .fetchOne()
    }

    private class ActivePetData(
        val pet: UserpetRecord?, val name: String?, val gender: Gender?, val rarity: Rarity?,
        val dailyBonusLevel: Int?, val cooldownReductionLevel: Int?
    )

    private fun getActivePetWithSkills(tx: DSLContext, activePetId: Int?): ActivePetData? {
        if (activePetId == null) return null

        val userPet = USERPET
        val pet = PET
        val userPetSkill = USERPETSKILL
        val petSkill = PETSKILL

        val dailyBonusLevel = DSL.max<Int?>(
            DSL.`when`<Int?>(DSL.lower(petSkill.NAME).eq("daily_bonus"), userPetSkill.LEVEL)
        ).`as`("daily_bonus_level")

        val dailyCooldownLevel = DSL.max<Int?>(
            DSL.`when`<Int?>(DSL.lower(petSkill.NAME).eq("daily_cooldown_reduction"), userPetSkill.LEVEL)
        ).`as`("daily_cooldown_level")

        val record: Record? =
            tx.select<Int?, String?, Int?, String?, Int?, Int?, Int?, Int?, Boolean?, Gender?, String?, Boolean?, LocalDateTime?, Int?, Int?, Int?, LocalDateTime?, LocalDateTime?, Array<String?>?, Rarity?, Int?, Int?>(
                userPet.ID, userPet.USERID, userPet.PETID, userPet.NAME,
                userPet.HUNGRY, userPet.LIFE, userPet.HAPPINESS, userPet.ENERGY,
                userPet.ISDEAD, userPet.GENDER, userPet.HUMOR, userPet.ISPREGNANT,
                userPet.PREGNANTENDAT, userPet.SPOUSEID, userPet.PARENT1ID,
                userPet.PARENT2ID, userPet.CREATEDAT, userPet.UPDATEDAT, userPet.FLAGS,
                pet.RARITY,
                dailyBonusLevel, dailyCooldownLevel
            )
                .from(userPet)
                .leftJoin(pet).on(pet.ID.eq(userPet.PETID))
                .leftJoin(userPetSkill).on(userPetSkill.USERPETID.eq(userPet.ID))
                .leftJoin(petSkill).on(petSkill.ID.eq(userPetSkill.SKILLID))
                .where(userPet.ID.eq(activePetId))
                .groupBy(userPet.ID, pet.RARITY)
                .fetchOne()

        if (record == null) return null

        val petRecord = UserpetRecord()
        petRecord.id = record.get<Int?>(userPet.ID)
        petRecord.userid = record.get<String?>(userPet.USERID)
        petRecord.petid = record.get<Int?>(userPet.PETID)
        petRecord.name = record.get<String?>(userPet.NAME)
        petRecord.hungry = record.get<Int?>(userPet.HUNGRY)
        petRecord.life = record.get<Int?>(userPet.LIFE)
        petRecord.happiness = record.get<Int?>(userPet.HAPPINESS)
        petRecord.energy = record.get<Int?>(userPet.ENERGY)
        petRecord.isdead = record.get<Boolean?>(userPet.ISDEAD)
        petRecord.gender = record.get<Gender?>(userPet.GENDER)
        petRecord.humor = record.get<String?>(userPet.HUMOR)
        petRecord.ispregnant = record.get<Boolean?>(userPet.ISPREGNANT)
        petRecord.pregnantendat = record.get<LocalDateTime?>(userPet.PREGNANTENDAT)
        petRecord.spouseid = record.get<Int?>(userPet.SPOUSEID)
        petRecord.parent1id = record.get<Int?>(userPet.PARENT1ID)
        petRecord.parent2id = record.get<Int?>(userPet.PARENT2ID)
        petRecord.createdat = record.get<LocalDateTime?>(userPet.CREATEDAT)
        petRecord.updatedat = record.get<LocalDateTime?>(userPet.UPDATEDAT)
        petRecord.flags = record.get<Array<String?>?>(userPet.FLAGS)

        return ActivePetData(
            petRecord,
            petRecord.name,
            petRecord.gender,
            record.get<Rarity?>(pet.RARITY),
            record.get<Int?>("daily_bonus_level", Int::class.java),
            record.get<Int?>("daily_cooldown_level", Int::class.java)
        )
    }

    private fun upsertCooldown(tx: DSLContext, userId: String?, nextDaily: LocalDateTime?) {
        tx.insertInto<CooldownRecord>(COOLDOWN)
            .columns<String?, String?, LocalDateTime?>(COOLDOWN.USERID, COOLDOWN.NAME, COOLDOWN.WILLENDIN)
            .values(userId, "daily", nextDaily)
            .onConflict(COOLDOWN.USERID, COOLDOWN.NAME)
            .doUpdate()
            .set<LocalDateTime?>(COOLDOWN.WILLENDIN, nextDaily)
            .execute()
    }

    override fun getSlashCommandData(): SlashCommandData {
        return Commands.slash("daily", "🎁 ✦ Claim your daily bonus")
            .setNameLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "diário")
            .setDescriptionLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "🎁 ✦ Pegue seu bônus diário")
            .setNameLocalization(DiscordLocale.SPANISH, "diario")
            .setDescriptionLocalization(DiscordLocale.SPANISH, "🎁 ✦ Reclama tu bono diario")
            .setNameLocalization(DiscordLocale.SPANISH_LATAM, "diario")
            .setDescriptionLocalization(DiscordLocale.SPANISH_LATAM, "🎁 ✦ Reclama tu bono diario")
            .setNameLocalization(DiscordLocale.ENGLISH_US, "daily")
            .setDescriptionLocalization(DiscordLocale.ENGLISH_US, "🎁 ✦ Claim your daily bonus")
    }

    companion object {
        private val log: Logger? = LoggerFactory.getLogger(Daily::class.java)

        private val RARITY_BONUS: MutableMap<Rarity?, Double?> = Map.of<Rarity?, Double?>(
            Rarity.COMUM, 1.0,
            Rarity.UNCOMUM, 1.2,
            Rarity.RARE, 1.5,
            Rarity.EPIC, 2.0,
            Rarity.LEGENDARY, 3.0
        )

        private val RARITY_COOLDOWN: MutableMap<Rarity?, Double?> = Map.of<Rarity?, Double?>(
            Rarity.COMUM, 1.0,
            Rarity.UNCOMUM, 0.9,
            Rarity.RARE, 0.8,
            Rarity.EPIC, 0.7,
            Rarity.LEGENDARY, 0.5
        )
    }
}