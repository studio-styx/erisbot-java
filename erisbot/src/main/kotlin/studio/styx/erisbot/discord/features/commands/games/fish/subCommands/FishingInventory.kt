package studio.styx.erisbot.discord.features.commands.games.fish.subCommands

import dev.minn.jda.ktx.coroutines.await
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import org.jooq.DSLContext
import shared.Colors
import shared.utils.Icon
import studio.styx.erisbot.generated.enums.Rarity
import studio.styx.erisbot.generated.tables.records.FishRecord
import studio.styx.erisbot.generated.tables.records.FishingrodRecord
import studio.styx.erisbot.generated.tables.records.UserfishRecord
import studio.styx.erisbot.generated.tables.records.UserfishingrodRecord
import studio.styx.erisbot.generated.tables.references.FISH
import studio.styx.erisbot.generated.tables.references.FISHINGROD
import studio.styx.erisbot.generated.tables.references.USERFISH
import studio.styx.erisbot.generated.tables.references.USERFISHINGROD
import utils.ComponentBuilder

private data class GroupedUserFish(
    val userFish: UserfishRecord,
    val fish: FishRecord,
    val quantity: Int
)

data class UserFishingRodWithDetails(
    val userFishingRod: UserfishingrodRecord,
    val fishingRod: FishingrodRecord
)

suspend fun fishInventoryCommand(event: SlashCommandInteractionEvent, dsl: DSLContext) {
    event.deferReply().await()

    dsl.transaction { config ->
        val tx = config.dsl()

        // Buscar todos os peixes do usuário
        val userFishes = tx.select(USERFISH.asterisk(), FISH.asterisk())
            .from(USERFISH)
            .innerJoin(FISH).on(USERFISH.FISHID.eq(FISH.ID))
            .where(USERFISH.USERID.eq(event.user.id))
            .fetch()
            .map { record ->
                UserfishRecord().apply {
                    from(record.into(USERFISH))
                } to FishRecord().apply {
                    from(record.into(FISH))
                }
            }

        // Buscar todas as varas de pesca do usuário
        val userFishingRods = tx.select(USERFISHINGROD.asterisk(), FISHINGROD.asterisk())
            .from(USERFISHINGROD)
            .innerJoin(FISHINGROD).on(USERFISHINGROD.FISHINGRODID.eq(FISHINGROD.ID))
            .where(USERFISHINGROD.USERID.eq(event.user.id))
            .fetch()
            .map { record ->
                UserFishingRodWithDetails(
                    userFishingRod = UserfishingrodRecord().apply {
                        from(record.into(USERFISHINGROD))
                    },
                    fishingRod = FishingrodRecord().apply {
                        from(record.into(FISHINGROD))
                    }
                )
            }

        // Verificar se o usuário não possui nada
        if (userFishes.isEmpty() && userFishingRods.isEmpty()) {
            ComponentBuilder.ContainerBuilder.create()
                .withColor(Colors.DANGER)
                .addText("${Icon.static.get("error")} | Você não possui nenhum peixe ou vara de pesca!")
                .reply(event)
            return@transaction
        }

        // Agrupar peixes por ID
        val groupedFish = userFishes.groupBy { it.second.id }
            .mapValues { (_, fishPairs) ->
                val firstFish = fishPairs.first()
                GroupedUserFish(
                    userFish = firstFish.first,
                    fish = firstFish.second,
                    quantity = fishPairs.size
                )
            }

        val rarityOrder = listOf(Rarity.COMUM, Rarity.UNCOMUM, Rarity.RARE, Rarity.EPIC, Rarity.LEGENDARY)

        // Criar lista de peixes agrupados por raridade
        val fishList = rarityOrder.mapNotNull { rarity ->
            val fishes = groupedFish.values
                .filter { it.fish.rarity == rarity }
                .sortedBy { it.fish.id }

            if (fishes.isEmpty()) {
                null
            } else {
                val rarityBlock = fishes.joinToString("\n") { fish ->
                    "`${fish.fish.id}` - **${fish.fish.name}** (${fish.quantity}x) (Raridade: **${fish.fish.rarity}**) - Preço: **${String.format("%.2f", fish.fish.price)}** stx"
                }
                "__${rarity}:__\n$rarityBlock"
            }
        }.joinToString("\n\n")

        // Criar lista de varas de pesca
        val fishingRodList = userFishingRods
            .sortedBy { it.fishingRod.id }
            .joinToString("\n") { userRod ->
                "`${userRod.fishingRod.id}` - **${userRod.fishingRod.name}** (Raridade: **${userRod.fishingRod.rarity}**) - Durabilidade: **${userRod.userFishingRod.durability}/${userRod.fishingRod.durability}**"
            }

        // Função para criar separador (ajuste conforme seu componente)
        fun createSeparator(): String = "─".repeat(40)

        // Montar resposta
        val fishSection = if (userFishes.isNotEmpty()) {
            "**Peixes:**\n$fishList"
        } else {
            "Nenhum peixe"
        }

        val fishingRodSection = if (userFishingRods.isNotEmpty()) {
            "**Varas de Pesca:**\n$fishingRodList"
        } else {
            "Nenhuma vara de pesca"
        }

        ComponentBuilder.ContainerBuilder.create()
            .withColor(Colors.PRIMARY)
            .addText("${Icon.static.get("info")} | Inventário de ${event.user.effectiveName}")
            .addText(fishSection)
            .addText(createSeparator())
            .addText(fishingRodSection)
            .reply(event)
    }
}