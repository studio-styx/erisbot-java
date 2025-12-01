package studio.styx.erisbot.discord.features.commands.economy.cassino

import database.utils.LogManage.CreateLog.Companion.create
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import org.jooq.Configuration
import org.jooq.DSLContext
import org.jooq.TransactionalRunnable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import shared.Colors
import studio.styx.erisbot.core.interfaces.CommandInterface
import studio.styx.erisbot.generated.enums.Rarity
import studio.styx.erisbot.generated.tables.records.UserRecord
import studio.styx.erisbot.generated.tables.references.*
import translates.TranslatesObjects.getCoinflip
import translates.commands.economy.cassino.CoinflipSide
import utils.ContainerRes
import java.math.BigDecimal
import java.util.*
import java.util.List
import java.util.Map
import java.util.function.Consumer
import kotlin.math.min


@Component
class Coinflip : CommandInterface {
    @Autowired
    lateinit var dsl: DSLContext

    private val res = ContainerRes()

    override fun getSlashCommandData(): SlashCommandData {
        val coinflipSide = OptionData(OptionType.STRING, "side", "side of coin", true)
            .addChoice("HEADS", "HEADS")
            .addChoice("TAILS", "TAILS")

        val amount = OptionData(OptionType.NUMBER, "amount", "amount to bet", true)
            .setMinValue(20)

        return Commands.slash("coinflip", "bet in coin side")
            .addOptions(coinflipSide, amount)
    }

    override suspend fun execute(event: SlashCommandInteractionEvent) {
        event.deferReply().queue(Consumer { hook: InteractionHook? ->
            dsl.transaction { config: Configuration? ->
                val tx = config!!.dsl()
                val userId = event.getUser().getId()
                val sideStr =
                    CoinflipSide.valueOf(event.getOption("side")!!.getAsString().uppercase(Locale.getDefault()))
                var amount = event.getOption("amount")!!.getAsDouble()

                val t = getCoinflip(event.getUserLocale().getLocale())

                val record = tx.select<BigDecimal?, Int?, Rarity?, String?, Int?>(
                    USER.MONEY,
                    USERPET.ID,
                    PET.RARITY,
                    PETSKILL.NAME.`as`("skill_name"),
                    USERPETSKILL.LEVEL.`as`("skill_level")
                )
                    .from(USER)
                    .leftJoin(USERPET).on(
                        USER.ACTIVEPETID.eq(USERPET.ID)
                            .and(USERPET.USERID.eq(userId))
                    )
                    .leftJoin(PET).on(
                        USERPET.PETID.eq(PET.ID)
                    )
                    .leftJoin(USERPETSKILL).on(
                        USERPET.ID.eq(USERPETSKILL.USERPETID)
                    )
                    .leftJoin(PETSKILL).on(
                        USERPETSKILL.SKILLID.eq(PETSKILL.ID)
                    )
                    .where(USER.ID.eq(userId))
                    .fetch()

                if (record.isEmpty()) {
                    res.setColor(Colors.DANGER).setText(t.notEnoughMoney())
                    hook!!.editOriginalComponents(res.build()).useComponentsV2().queue()
                    return@transaction
                }

                val userMoney = record.get(0).get<BigDecimal?>(USER.MONEY).toDouble()
                if (userMoney < 15) {
                    res.setColor(Colors.DANGER).setText(t.notEnoughMoney())
                    hook!!.editOriginalComponents(res.build()).useComponentsV2().queue()
                    return@transaction
                }

                if (userMoney < amount) {
                    amount = userMoney
                }

                // Aggregate skills and pet rarity from the record
                val baseChance = 0.5
                var totalChance = baseChance
                val baseAmountBonus = 0.2
                var totalAmountBonus = baseAmountBonus

                // Define rarity bonuses
                val rarityLuckBonus = Map.of<String?, Double?>(
                    "COMUM", 0.02,
                    "UNCOMUM", 0.04,
                    "RARE", 0.06,
                    "EPIC", 0.08,
                    "LEGENDARY", 0.10
                )

                val rarityAmountBonus = Map.of<String?, Double?>(
                    "COMUM", 0.4,
                    "UNCOMUM", 0.6,
                    "RARE", 0.8,
                    "EPIC", 1.2,
                    "LEGENDARY", 1.5
                )

                var petRarity: Rarity? = null
                var coinflipLuckLevel = 0
                var coinflipBonusLevel = 0

                for (r in record) {
                    if (petRarity == null) {
                        val rarity = r.get<Rarity?>(PET.RARITY)
                        if (rarity != null) {
                            petRarity = rarity
                        }
                    }

                    val skillName = r.get<String?>("skill_name", String::class.java)
                    val skillLevel = r.get<Int?>("skill_level", Int::class.java)

                    if (skillName != null && skillLevel != null) {
                        if ("coinflip_luck" == skillName) {
                            coinflipLuckLevel = skillLevel
                        }
                        if ("coinflip_bonus" == skillName) {
                            coinflipBonusLevel = skillLevel
                        }
                    }
                }

                if (petRarity != null) {
                    totalChance += rarityLuckBonus.getOrDefault(
                        petRarity.toString(),
                        0.0
                    ) + (coinflipLuckLevel * 0.05)
                    totalAmountBonus += rarityAmountBonus.getOrDefault(
                        petRarity.toString(),
                        0.0
                    ) + (coinflipBonusLevel * 0.05)
                }

                totalChance = min(totalChance, 0.7)
                totalAmountBonus = min(totalAmountBonus, 2.5)

                val isHeads = Math.random() < totalChance

                // CORREÇÃO: Compare o enum diretamente, não com strings
                val userWins = (isHeads && sideStr == CoinflipSide.HEADS) || (!isHeads && sideStr == CoinflipSide.TAILS)
                if (userWins) {
                    val wonValue = amount * totalAmountBonus

                    tx.update<UserRecord>(USER)
                        .set<BigDecimal?>(USER.MONEY, USER.MONEY.plus(wonValue))
                        .where(USER.ID.eq(userId))
                        .execute()

                    hook!!.editOriginalComponents(
                        res.setColor(Colors.SUCCESS).setText(t.won(sideStr, wonValue)).build()
                    ).useComponentsV2().queue()

                    create()
                        .setUserId(event.getUser().getId())
                        .setMessage(t.logWon(sideStr, wonValue))
                        .setLevel(3)
                        .setTags(List.of<String>("coinflip", "economy", "win", sideStr.toString()))
                        .insert(tx)
                } else {
                    tx.update<UserRecord>(USER)
                        .set<BigDecimal?>(USER.MONEY, USER.MONEY.minus(amount))
                        .where(USER.ID.eq(userId))
                        .execute()

                    hook!!.editOriginalComponents(res.setColor(Colors.DANGER).setText(t.lose(sideStr, amount)).build())
                        .useComponentsV2().queue()

                    create()
                        .setUserId(event.getUser().getId())
                        .setMessage(t.logLose(sideStr, amount))
                        .setLevel(3)
                        .setTags(List.of<String>("coinflip", "economy", "lose", sideStr.toString()))
                        .insert(tx)
                }
            }
        })
    }
}
