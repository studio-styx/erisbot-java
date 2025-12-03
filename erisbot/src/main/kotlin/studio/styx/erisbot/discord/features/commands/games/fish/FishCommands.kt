package studio.styx.erisbot.discord.features.commands.games.fish

import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import shared.Cache
import studio.styx.erisbot.core.interfaces.CommandInterface
import studio.styx.erisbot.discord.features.commands.games.fish.subCommands.fishCommand
import studio.styx.erisbot.discord.features.commands.games.fish.subCommands.fishInventoryCommand
import studio.styx.erisbot.discord.features.commands.games.fish.subCommands.fishSellCommand
import studio.styx.erisbot.discord.features.commands.games.fish.subCommands.fishingRodBuyCommand
import studio.styx.erisbot.generated.enums.Rarity
import studio.styx.erisbot.generated.tables.records.FishRecord
import studio.styx.erisbot.generated.tables.records.FishingrodRecord
import studio.styx.erisbot.generated.tables.references.FISH
import studio.styx.erisbot.generated.tables.references.FISHINGROD
import studio.styx.erisbot.generated.tables.references.USERFISH

@Component
class FishCommands : CommandInterface {
    @Autowired
    lateinit var dsl: DSLContext

    override fun getSlashCommandData(): SlashCommandData {
        val fishSubCommand = SubcommandData("fish", "pescar")
        val inventorySubCommand = SubcommandData("inventory", "inventário de pesca")
        val sellSubCommand = SubcommandData("sell", "venda peixes do seu inventário")
            .addOptions(
                OptionData(OptionType.STRING, "id", "id do peixe a ser vendido")
                    .setRequired(true)
                    .setAutoComplete(true)
            )
        val fishingRodBuySubCommand = SubcommandData("fishing_rod_buy", "compre uma vara de pesca para poder pescar")
            .addOptions(
                OptionData(OptionType.STRING, "id", "id da vara de pesca a ser comprada")
                    .setRequired(true)
                    .setAutoComplete(true)
            )

        return Commands.slash("fishing", "comandos de pescaria")
            .addSubcommands(fishSubCommand, inventorySubCommand, sellSubCommand, fishingRodBuySubCommand)
    }

    override suspend fun execute(event: SlashCommandInteractionEvent) {
        when (event.subcommandName) {
            "sell" -> fishSellCommand(event, dsl)
            "inventory" -> fishInventoryCommand(event, dsl)
            "fishing_rod_buy" -> fishingRodBuyCommand(event, dsl)
            "fish" -> fishCommand(event, dsl)
        }
    }

    override suspend fun onAutoComplete(event: CommandAutoCompleteInteractionEvent) {
        val subCommand = event.subcommandName

        when (subCommand) {
            "sell" -> {
                val input = event.focusedOption.value
                val userId = event.user.id
                val autoComplete = SellFishSubCommandAutoComplete(input, userId, dsl)
                val fishes = autoComplete.getFiltered()

                if (fishes.isEmpty()) {
                    event.replyChoices(
                        Command.Choice("Nenhum peixe encontrado", "none")
                    ).queue()
                    return
                }

                val choices = fishes.take(24).map { groupedFish ->
                    val displayName = if (groupedFish.quantity > 1)
                        "${groupedFish.fish.name} (x${groupedFish.quantity})"
                    else
                        groupedFish.fish.name!!

                    Command.Choice(displayName, groupedFish.fish.id.toString())
                }.toMutableList()

                // Adicionar ao primeiro
                choices.add(0, Command.Choice("Vender todos os peixes (${fishes.sumOf { it.quantity }})", "all"))

                event.replyChoices(choices).queue()
            }
            "fishing_rod_buy" -> {
                val input = event.focusedOption.value

                val autoComplete = FishingRodBuySubCommandAutoComplete(input, dsl)
                val fishes = autoComplete.getFiltered()

                if (fishes.isEmpty()) {
                    event.replyChoices(
                        Command.Choice("Nenhuma vara de pesca encontrada", "none")
                    )
                    return
                }

                val choices = fishes.take(25).map {
                    val rarity = when (it.rarity) {
                        Rarity.UNCOMUM -> "Incomum"
                        Rarity.RARE -> "Raro"
                        Rarity.LEGENDARY -> "Lendário"
                        Rarity.EPIC -> "Épico"
                        else -> "Comum"
                    }
                    Command.Choice("${it.name!!} ($rarity) - ${it.price}", it.id.toString())
                }

                event.replyChoices(choices).queue()
            }
        }
    }

    private class FishingRodBuySubCommandAutoComplete(
        private val focused: String,
        private val dsl: DSLContext
    ) {
        fun getFishingRods(): List<FishingrodRecord> {
            val key = "fishingRods:toBuy"

            val cached = Cache.get<List<FishingrodRecord>>(key)
            if (cached != null) return cached

            val fishingRods = dsl.selectFrom(FISHINGROD)
                .orderBy(FISHINGROD.PRICE.desc())
                .fetch()

            Cache.set(key, fishingRods)
            return fishingRods
        }

        fun getFiltered(): List<FishingrodRecord> {
            val fishingRods = getFishingRods()

            if (focused.isBlank()) return fishingRods

            return fishingRods.filter {
                it.name!!.contains(focused, ignoreCase = true)
            }
        }

    }

    private data class GroupedUserFish(
        val fish: FishRecord,
        val quantity: Int
    )

    private class SellFishSubCommandAutoComplete(
        private val focused: String,
        private val userId: String,
        private val dsl: DSLContext
    ) {
        fun getFishes(): List<GroupedUserFish> {
            val key = "fishs:toSell:$userId"

            val cached = Cache.get<List<GroupedUserFish>>(key)
            if (cached != null) return cached

            val fishes = dsl.select(
                FISH.asterisk(),
                DSL.count().`as`("quantity")
            )
                .from(FISH)
                .innerJoin(USERFISH).on(USERFISH.FISHID.eq(FISH.ID))
                .where(USERFISH.USERID.eq(userId))
                .groupBy(FISH.ID, FISH.NAME, FISH.PRICE)
                .orderBy(FISH.PRICE.desc())
                .fetch()

            val fishesFormatted = fishes.map { record ->
                val fishRecord = record.into(FISH)
                val quantity = record.get("quantity", Int::class.java)

                GroupedUserFish(fishRecord, quantity)
            }

            Cache.set(key, fishesFormatted)
            return fishesFormatted
        }

        fun getFiltered(): List<GroupedUserFish> {
            val fishes = getFishes()

            if (focused.isBlank()) return fishes

            return fishes.filter {
                it.fish.name!!.contains(focused, ignoreCase = true)
            }
        }
    }
}