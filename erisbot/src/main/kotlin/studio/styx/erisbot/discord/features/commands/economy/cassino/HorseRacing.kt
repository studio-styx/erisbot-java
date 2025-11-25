package studio.styx.erisbot.discord.features.commands.economy.cassino

import database.utils.DatabaseUtils.getOrCreateUser
import database.utils.LogManage
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.DiscordLocale
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import org.jooq.Configuration
import org.jooq.DSLContext
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
import translates.TranslatesObjects.getHorseRacing
import translates.commands.economy.cassino.HorseRacingTranslateInterface
import utils.ComponentBuilder
import java.awt.Color
import java.math.BigDecimal
import java.util.*
import java.util.Map
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import kotlin.math.min


@Component
class HorseRacing : CommandInterface {
    @Autowired
    lateinit var dsl: DSLContext

    private val random = Random()
    private val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(1)

    // Bônus por raridade para luck
    private val rarityLuckBonus: MutableMap<Rarity?, Double?> = Map.of<Rarity?, Double?>(
        Rarity.COMUM, 0.02,
        Rarity.UNCOMUM, 0.04,
        Rarity.RARE, 0.06,
        Rarity.EPIC, 0.08,
        Rarity.LEGENDARY, 0.10
    )

    // Bônus por raridade para amount
    private val rarityAmountBonus: MutableMap<Rarity?, Double?> = Map.of<Rarity?, Double?>(
        Rarity.COMUM, 0.1,
        Rarity.UNCOMUM, 0.2,
        Rarity.RARE, 0.3,
        Rarity.EPIC, 0.4,
        Rarity.LEGENDARY, 0.5
    )

    private var horses: MutableMap<String, Horse>? = null
    private var selectedHorse: String? = null
    private var amount = 0.0
    private var winMultiplier = 1.5

    override fun getSlashCommandData(): SlashCommandData {
        val horce = OptionData(OptionType.STRING, "horse", "Horse to bet on", true)
            .addChoices(
                Command.Choice("Purple", "purple")
                    .setNameLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "roxo")
                    .setNameLocalization(DiscordLocale.SPANISH, "morado"),
                Command.Choice("Blue", "blue")
                    .setNameLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "azul")
                    .setNameLocalization(DiscordLocale.SPANISH, "azul"),
                Command.Choice("Green", "green")
                    .setNameLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "verde")
                    .setNameLocalization(DiscordLocale.SPANISH, "verde"),
                Command.Choice("Yellow", "yellow")
                    .setNameLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "amarelo")
                    .setNameLocalization(DiscordLocale.SPANISH, "amarillo"),
                Command.Choice("Orange", "orange")
                    .setNameLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "laranja")
                    .setNameLocalization(DiscordLocale.SPANISH, "naranja"),
                Command.Choice("Red", "red")
                    .setNameLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "vermelho")
                    .setNameLocalization(DiscordLocale.SPANISH, "rojo"),
                Command.Choice("Pink", "pink")
                    .setNameLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "rosa")
                    .setNameLocalization(DiscordLocale.SPANISH, "rosa"),
                Command.Choice("Brown", "brown")
                    .setNameLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "marrom")
                    .setNameLocalization(DiscordLocale.SPANISH, "marrón")
            )

        val amount = OptionData(OptionType.NUMBER, "amount", "amount to bet", true)
            .setMinValue(50)
            .setNameLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "valor")
            .setDescriptionLocalization(DiscordLocale.SPANISH, "valor")

        return Commands.slash("horse-racing", "Bet on horse racing")
            .setNameLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "corrida-de-cavalos")
            .setNameLocalization(DiscordLocale.SPANISH, "carreras-de-caballos")
            .setDescriptionLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "apostar na corrida de cavalos")
            .setDescriptionLocalization(DiscordLocale.SPANISH, "apostar en carreras de caballos")
            .addOptions(horce, amount)
    }

    override fun execute(event: SlashCommandInteractionEvent) {
        event.deferReply().queue(Consumer { hook: InteractionHook ->
            dsl.transaction { config: Configuration ->
                val tx = config.dsl()
                val userId = event.user.id

                // Obtém as opções
                this.amount = event.getOption("amount")!!.asDouble
                this.selectedHorse = event.getOption("horse")!!.asString

                val userData = getOrCreateUser(tx, userId)
                val t = getHorseRacing(event.userLocale.locale)

                // Inicializa os cavalos
                initializeHorses(t)

                // Verifica se tem dinheiro suficiente
                if (amount < 50) {
                    hook.editOriginalComponents(
                        ComponentBuilder.ContainerBuilder.create()
                            .addText(t.notEnoughMoney)
                            .withColor(Colors.DANGER)
                            .build()
                    ).useComponentsV2().queue()
                    return@transaction
                }

                // Ajusta o amount se for maior que o dinheiro disponível
                if (userData.money!!.toDouble() < amount) {
                    this.amount = userData.money!!.toDouble()
                }

                // Busca o pet ativo e suas skills
                val activePet = getActivePetWithSkills(tx, userId)

                // Encontra as skills
                val horseRacingLuck = findHorseRacingSkill(activePet, tx, "horse_racing_luck")
                val horseRacingBonus = findHorseRacingSkill(activePet, tx, "horse_racing_bonus")

                // Calcula a chance do cavalo apostado ganhar
                val userHorseChance = calculateWinChance(activePet, horseRacingLuck)

                // Inicia a corrida
                startRace(event, hook, tx, userId, t, activePet, horseRacingBonus, userHorseChance)
            }
        })
    }

    private fun initializeHorses(t: HorseRacingTranslateInterface) {
        horses = HashMap<String, Horse>()
        horses!!.put("purple", Horse(t.horses.purple.name, t.horses.purple.emoji, t.horses.purple.colorEmoji))
        horses!!.put("blue", Horse(t.horses.blue.name, t.horses.blue.emoji, t.horses.blue.colorEmoji))
        horses!!.put("green", Horse(t.horses.green.name, t.horses.green.emoji, t.horses.green.colorEmoji))
        horses!!.put("yellow", Horse(t.horses.yellow.name, t.horses.yellow.emoji, t.horses.yellow.colorEmoji))
        horses!!.put("orange", Horse(t.horses.orange.name, t.horses.orange.emoji, t.horses.orange.colorEmoji))
        horses!!.put("red", Horse(t.horses.red.name, t.horses.red.emoji, t.horses.red.colorEmoji))
        horses!!.put("pink", Horse(t.horses.pink.name, t.horses.pink.emoji, t.horses.pink.colorEmoji))
        horses!!.put("brown", Horse(t.horses.brown.name, t.horses.brown.emoji, t.horses.brown.colorEmoji))
    }

    private fun getActivePetWithSkills(tx: DSLContext, userId: String?): PetRecord? {
        return tx.select(PET.asterisk())
            .from(PET)
            .join(USER).on(USER.ACTIVEPETID.eq(PET.ID))
            .where(USER.ID.eq(userId))
            .fetchOneInto<PetRecord?>(PetRecord::class.java)
    }

    private fun findHorseRacingSkill(activePet: PetRecord?, tx: DSLContext, skillName: String?): UserpetskillRecord? {
        if (activePet == null) return null

        return tx.select(USERPETSKILL.asterisk())
            .from(USERPETSKILL)
            .join(PETSKILL).on(USERPETSKILL.SKILLID.eq(PETSKILL.ID))
            .where(
                USERPETSKILL.USERPETID.eq(activePet.id)
                    .and(PETSKILL.NAME.eq(skillName))
            )
            .fetchOneInto<UserpetskillRecord?>(UserpetskillRecord::class.java)
    }

    private fun calculateWinChance(activePet: PetRecord?, horseRacingLuck: UserpetskillRecord?): Double {
        val baseWinChance = 0.2 // 20% base
        var userHorseChance = baseWinChance

        if (horseRacingLuck != null && activePet != null) {
            userHorseChance += rarityLuckBonus.getOrDefault(activePet.rarity, 0.0)!! + (horseRacingLuck.level!! * 0.02)
        }

        return min(userHorseChance, 0.5) // Máximo 50%
    }

    private fun startRace(
        event: SlashCommandInteractionEvent, hook: InteractionHook,
        tx: DSLContext, userId: String?, t: HorseRacingTranslateInterface,
        activePet: PetRecord?, horseRacingBonus: UserpetskillRecord?, userHorseChance: Double
    ) {
        // Embed inicial

        val initialEmbed = createRaceEmbed(t, false, null, 1.5)
        hook.editOriginalEmbeds(initialEmbed).queue()

        // Inicia a corrida após 2 segundos
        scheduler.schedule({
            moveHorses(
                event,
                hook,
                tx,
                userId,
                t,
                activePet,
                horseRacingBonus,
                userHorseChance,
                0
            )
        }, 2, TimeUnit.SECONDS)
    }

    private fun moveHorses(
        event: SlashCommandInteractionEvent, hook: InteractionHook,
        tx: DSLContext, userId: String?, t: HorseRacingTranslateInterface,
        activePet: PetRecord?, horseRacingBonus: UserpetskillRecord?, userHorseChance: Double, round: Int
    ) {
        // Determina o vencedor predeterminado

        val predeterminedWinner = determineWinner(userHorseChance)

        // Move os cavalos
        val raceFinished = moveAllHorses(predeterminedWinner)

        // Atualiza a mensagem
        val raceEmbed = createRaceEmbed(t, false, null, 1.5)
        hook.editOriginalEmbeds(raceEmbed).queue()

        // Verifica se a corrida terminou
        val actualWinner = this.actualWinner
        if (raceFinished && actualWinner != null) {
            val userWon = actualWinner == selectedHorse

            // Calcula o multiplicador se ganhou
            if (userWon) {
                calculateWinMultiplier(activePet, horseRacingBonus)
            }

            // Atualiza o dinheiro do usuário
            updateUserBalance(tx, userId, userWon)

            // Cria embed final
            val finalEmbed = createRaceEmbed(t, true, actualWinner, if (userWon) winMultiplier else 1.5)
            hook.editOriginalEmbeds(finalEmbed).queue()

            // Registra log
            registerLog(event, userWon, actualWinner)

            return
        }

        // Continua a corrida se não terminou
        if (!raceFinished) {
            scheduler.schedule(Runnable {
                moveHorses(
                    event,
                    hook,
                    tx,
                    userId,
                    t,
                    activePet,
                    horseRacingBonus,
                    userHorseChance,
                    round + 1
                )
            }, 2, TimeUnit.SECONDS)
        }
    }

    private fun determineWinner(userHorseChance: Double): String? {
        if (random.nextDouble() < userHorseChance) {
            return selectedHorse
        }

        // Sorteia entre os outros cavalos
        val otherHorses: MutableList<String?> = ArrayList<String?>(horses!!.keys)
        otherHorses.remove(selectedHorse)
        return otherHorses.get(random.nextInt(otherHorses.size))
    }

    private fun moveAllHorses(predeterminedWinner: String?): Boolean {
        var raceFinished = false

        for (horseKey in horses!!.keys) {
            val horse: Horse = horses!!.get(horseKey)!!

            var moveChance = 0.7 // Chance base
            var moveDistance = 1 + random.nextInt(2) // 1-2 posições

            // Vantagem para o vencedor predeterminado
            if (horseKey == predeterminedWinner) {
                moveChance = 0.9
                moveDistance = 1 + random.nextInt(3) // 1-3 posições
            }

            if (random.nextDouble() < moveChance) {
                horse.position += moveDistance
                if (horse.position >= 14) {
                    raceFinished = true
                }
            }
        }

        return raceFinished
    }

    private val actualWinner: String?
        get() {
            for (entry in horses!!.entries) {
                if (entry.value.position >= 14) {
                    return entry.key
                }
            }
            return null
        }

    private fun calculateWinMultiplier(activePet: PetRecord?, horseRacingBonus: UserpetskillRecord?) {
        winMultiplier = 1.5 // Multiplicador base

        if (horseRacingBonus != null && activePet != null) {
            winMultiplier += rarityAmountBonus.getOrDefault(activePet.rarity, 0.0)!! + (horseRacingBonus.level!! * 0.05)
            winMultiplier = min(winMultiplier, 3.0) // Limite máximo de 3x
        }
    }

    private fun updateUserBalance(tx: DSLContext, userId: String?, userWon: Boolean) {
        if (userWon) {
            val winAmount = amount * winMultiplier
            tx.update<UserRecord>(USER)
                .set<BigDecimal?>(USER.MONEY, USER.MONEY.add(BigDecimal.valueOf(winAmount)))
                .where(USER.ID.eq(userId))
                .execute()
        } else {
            tx.update<UserRecord>(USER)
                .set<BigDecimal?>(USER.MONEY, USER.MONEY.subtract(BigDecimal.valueOf(amount)))
                .where(USER.ID.eq(userId))
                .execute()
        }
    }

    private fun createRaceEmbed(
        t: HorseRacingTranslateInterface,
        isFinished: Boolean,
        winner: String?,
        multiplier: Double
    ): MessageEmbed {
        val builder = EmbedBuilder()

        if (isFinished && winner != null) {
            builder.setTitle(t.end.title)
                .setDescription(createRaceTrack())
                .setColor(Color.decode(if (winner == selectedHorse) Colors.SUCCESS else Colors.DANGER))
                .addField(
                    t.end.fields.winner.name,
                    t.end.fields.winner.value(horses!!.get(winner)!!.emoji, horses!!.get(winner)!!.name), true
                )
                .addField(
                    t.end.fields.bet.name,
                    t.end.fields.bet.value(horses!!.get(selectedHorse!!)!!.emoji, horses!!.get(selectedHorse!!)!!.name),
                    true
                )
                .addField(
                    t.end.fields.result.name,
                    t.end.fields.result.value(winner == selectedHorse, amount, multiplier), false
                )
        } else {
            builder.setTitle(t.playing.title)
                .setDescription(createRaceTrack())
                .setColor(Color.decode(Colors.PRIMARY))
        }

        return builder.build()
    }

    private fun createRaceTrack(): String {
        val description = StringBuilder()
        val trackLength = 15

        for (horse in horses!!.values) {
            val progress = "―".repeat(trackLength)
            val position = min(horse.position, trackLength - 1)

            // Constrói a pista como string diretamente
            val track = progress.substring(0, position) +
                    horse.emoji +
                    progress.substring(position + 1)

            description.append("**")
                .append(horse.name)
                .append("** ")
                .append(track)
                .append(" ")
                .append(horse.colorEmoji)
                .append("\n")
        }

        return description.toString()
    }

    private fun registerLog(event: SlashCommandInteractionEvent, userWon: Boolean, winner: String?) {
        val logMessage: String?
        val tags =
            ArrayList<String>(mutableListOf<String?>("cassino", "transaction", "horse-racing"))

        if (userWon) {
            logMessage = String.format(
                "Horse Racing WIN | Horse: %s | Bet: %.2f | Multiplier: %.1fx | Win: %.2f",
                horses!!.get(winner!!)!!.name, amount, winMultiplier, amount * winMultiplier
            )
            tags.add("sum")
        } else {
            logMessage = String.format(
                "Horse Racing LOSE | Horse: %s | Bet: %.2f | Winner: %s",
                horses!!.get(selectedHorse!!)!!.name, amount, horses!!.get(winner!!)!!.name
            )
            tags.add("sub")
        }

        LogManage.CreateLog.create()
            .setUserId(event.getUser().getId())
            .setMessage(logMessage)
            .setLevel(6)
            .setTags(tags)
    }

    // Classe auxiliar para representar um cavalo
    private class Horse(var name: String, var emoji: String, var colorEmoji: String?) {
        var position: Int = 0
    }
}