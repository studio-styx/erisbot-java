package studio.styx.erisbot.discord.features.commands.economy.cassino

import database.utils.DatabaseUtils.getOrCreateUser
import database.utils.LogManage
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import org.jooq.Configuration
import org.jooq.DSLContext
import org.jooq.TransactionalRunnable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import shared.Colors
import studio.styx.erisbot.core.interfaces.CommandInterface
import studio.styx.erisbot.generated.enums.Rarity
import studio.styx.erisbot.generated.tables.records.PetRecord
import studio.styx.erisbot.generated.tables.records.UserRecord
import studio.styx.erisbot.generated.tables.records.UserpetskillRecord
import studio.styx.erisbot.generated.tables.references.PET
import studio.styx.erisbot.generated.tables.references.PETSKILL
import studio.styx.erisbot.generated.tables.references.USER
import studio.styx.erisbot.generated.tables.references.USERPETSKILL
import translates.TranslatesObjects.getSlotsMachine
import utils.ComponentBuilder
import java.math.BigDecimal
import java.util.*
import java.util.List
import java.util.Map
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import kotlin.math.min


@Component
class Slots : CommandInterface {
    @Autowired
    lateinit var dsl: DSLContext

    private val random = Random()
    private val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(1)

    // Bônus por raridade
    private val rarityBonus: MutableMap<Rarity?, Double?> = Map.of<Rarity?, Double?>(
        Rarity.COMUM, 0.05,
        Rarity.UNCOMUM, 0.1,
        Rarity.RARE, 0.15,
        Rarity.EPIC, 0.2,
        Rarity.LEGENDARY, 0.25
    )

    private val slots = arrayOf<String>("🍒", "🍊", "🍋", "🍉", "🍇", "🍓", "🍎", "🍐")

    override fun getSlashCommandData(): SlashCommandData {
        return Commands.slash("slots", "Jogue na slot machine")
            .addOption(OptionType.NUMBER, "amount", "Quantidade para apostar", true)
    }


    override suspend fun execute(event: SlashCommandInteractionEvent) {
        event.deferReply().queue(Consumer { hook: InteractionHook ->
            dsl.transaction(TransactionalRunnable { config: Configuration ->
                val tx = config.dsl()
                val userId = event.getUser().getId()

                // Obtém o amount da opção
                var amount = event.getOption("amount")!!.getAsDouble()

                val userData = getOrCreateUser(tx, userId)
                val t = getSlotsMachine(event.getUserLocale().getLocale())

                // Verifica se tem dinheiro suficiente
                if (userData.money!!.toDouble() < 25) {
                    hook.editOriginalComponents(
                        ComponentBuilder.ContainerBuilder.create()
                            .addText(t.notEnoughMoney)
                            .withColor(Colors.DANGER)
                            .build()
                    ).useComponentsV2().queue()
                    return@TransactionalRunnable
                }

                // Ajusta o amount se for maior que o dinheiro disponível
                if (userData.money!!.toDouble() < amount) {
                    amount = userData.money!!.toDouble()
                }

                // Busca o pet ativo e suas skills
                val activePet = getActivePetWithSkills(tx, userId)

                // Encontra a skill slots_luck
                val slotsLuckySkill = findSlotsLuckSkill(activePet, tx)

                // Chance base SEM pet
                val baseChance = 0.15 // 15% base

                // Calcula chance total
                var totalChance = baseChance

                if (slotsLuckySkill != null && activePet != null) {
                    // Adiciona: bônus da raridade + bônus do nível da skill
                    val rarityBonusValue: Double = rarityBonus.getOrDefault(activePet.rarity, 0.0)!!
                    val skillBonus = slotsLuckySkill.level!! * 0.05
                    totalChance += rarityBonusValue + skillBonus
                }

                val finalChance = min(totalChance, 0.6)

                // Determina se é jackpot forçado
                val isForcedJackpot = random.nextDouble() < finalChance
                val slot1: String
                val slot2: String
                val slot3: String

                if (isForcedJackpot) {
                    val winningSymbol = slots[random.nextInt(slots.size)]
                    slot3 = winningSymbol
                    slot2 = slot3
                    slot1 = slot2
                } else {
                    slot1 = slots[random.nextInt(slots.size)]
                    slot2 = slots[random.nextInt(slots.size)]
                    slot3 = slots[random.nextInt(slots.size)]
                }

                val isWin = slot1 == slot2 && slot2 == slot3

                // Embed inicial
                val initialContainer = ComponentBuilder.ContainerBuilder.create()
                    .addText(t.title)
                    .addDivider(false)
                    .addText(t.slot1(slot1))
                    .withColor(Colors.PRIMARY)
                    .build()

                val finalAmount = amount
                hook.editOriginalComponents(initialContainer).useComponentsV2().queue(Consumer { success: Message? ->
                    // Primeira animação após 2 segundos
                    scheduler.schedule(Runnable {
                        val secondContainer = ComponentBuilder.ContainerBuilder.create()
                            .addText(t.title)
                            .addDivider(false)
                            .addText(t.slot2(slot1, slot2))
                            .withColor(Colors.PRIMARY)
                            .build()
                        hook.editOriginalComponents(secondContainer).useComponentsV2()
                            .queue(Consumer { secondSuccess: Message ->
                                // Segunda animação após mais 2 segundos
                                scheduler.schedule(Runnable {
                                    val winAmount = finalAmount * 0.6
                                    // Atualiza o saldo do usuário
                                    updateUserBalance(tx, userId, isWin, if (isWin) winAmount else finalAmount)

                                    // Registra o log
                                    registerLog(event, isWin, winAmount, finalAmount)

                                    // Embed final
                                    val description = if (isWin)
                                        t.winMessage(slot1, slot2, slot3, winAmount)
                                    else
                                        t.loseMessage(slot1, slot2, slot3, finalAmount)

                                    val finalContainer = ComponentBuilder.ContainerBuilder.create()
                                        .addText(t.title)
                                        .addDivider(false)
                                        .addText(description)
                                        .withColor(if (isWin) Colors.SUCCESS else Colors.DANGER)
                                        .build()
                                    hook.editOriginalComponents(finalContainer).useComponentsV2().queue()
                                }, 2, TimeUnit.SECONDS)
                            })
                    }, 2, TimeUnit.SECONDS)
                })
            })
        })
    }

    private fun getActivePetWithSkills(tx: DSLContext, userId: String?): PetRecord? {
        return tx.select(PET.asterisk())
            .from(PET)
            .join(USER).on(USER.ACTIVEPETID.eq(PET.ID))
            .where(USER.ID.eq(userId))
            .fetchOneInto<PetRecord?>(PetRecord::class.java)
    }

    private fun findSlotsLuckSkill(activePet: PetRecord?, tx: DSLContext): UserpetskillRecord? {
        if (activePet == null) return null

        return tx.select(USERPETSKILL.asterisk())
            .from(PETSKILL)
            .join(USERPETSKILL).on(USERPETSKILL.SKILLID.eq(PETSKILL.ID))
            .where(
                USERPETSKILL.USERPETID.eq(activePet.id)
                    .and(PETSKILL.NAME.eq("slots_luck"))
            )
            .fetchOneInto<UserpetskillRecord?>(UserpetskillRecord::class.java)
    }

    private fun updateUserBalance(tx: DSLContext, userId: String?, isWin: Boolean, amount: Double) {
        if (isWin) {
            tx.update<UserRecord>(USER)
                .set<BigDecimal?>(USER.MONEY, USER.MONEY.add(BigDecimal.valueOf(amount)))
                .where(USER.ID.eq(userId))
                .execute()
        } else {
            tx.update<UserRecord>(USER)
                .set<BigDecimal?>(USER.MONEY, USER.MONEY.subtract(BigDecimal.valueOf(amount)))
                .where(USER.ID.eq(userId))
                .execute()
        }
    }

    private fun registerLog(event: SlashCommandInteractionEvent, isWin: Boolean, winAmount: Double, betAmount: Double) {
        val logMessage = String.format(
            "Slots: %s | %s | Bet: %.2f | Win: %.2f",
            event.getUser().getGlobalName(),
            if (isWin) "WIN" else "LOSE",
            betAmount,
            if (isWin) winAmount else 0.0
        )

        LogManage.CreateLog.create()
            .setUserId(event.getUser().getId())
            .setMessage(logMessage)
            .setLevel(3)
            .setTags(List.of<String>("Slots", "cassino", "economy", if (isWin) "win" else "lose"))
    }
}
