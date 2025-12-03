package studio.styx.erisbot.discord.features.commands.games.fish.subCommands

import dev.minn.jda.ktx.coroutines.await
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import org.jooq.DSLContext
import shared.Cache
import shared.Colors
import shared.utils.Icon
import studio.styx.erisbot.generated.enums.Rarity
import studio.styx.erisbot.generated.tables.records.FishingrodRecord
import studio.styx.erisbot.generated.tables.records.UserfishingrodRecord
import studio.styx.erisbot.generated.tables.references.FISHINGROD
import studio.styx.erisbot.generated.tables.references.USERFISHINGROD
import utils.ComponentBuilder
import java.util.concurrent.TimeUnit


internal suspend fun fishCommand(event: SlashCommandInteractionEvent, dsl: DSLContext) {
    event.deferReply().await()

    val userRods = dsl.select(USERFISHINGROD.asterisk(), FISHINGROD.asterisk())
        .from(USERFISHINGROD)
        .innerJoin(FISHINGROD).on(USERFISHINGROD.FISHINGRODID.eq(FISHINGROD.ID))
        .where(USERFISHINGROD.USERID.eq(event.user.id))
        .fetch {
            UserFishingRodWithDetails(
                it.into(UserfishingrodRecord::class.java),
                it.into(FishingrodRecord::class.java)
            )
        }

    if (userRods.isEmpty()) {
        ComponentBuilder.ContainerBuilder.create()
            .withColor(Colors.DANGER)
            .addText("${Icon.static.get("denied")} | Você precisa de uma vara de pesca para poder pescar!")
            .reply(event)
        return
    }

    val rarityOrder = mapOf(
        Rarity.LEGENDARY to 5,
        Rarity.EPIC to 4,
        Rarity.RARE to 3,
        Rarity.UNCOMUM to 2,
        Rarity.COMUM to 1
    )

    // Encontrar a melhor vara de pesca
    val betterFishingRod = userRods.sortedWith(
        compareByDescending<UserFishingRodWithDetails> { rod ->
            rarityOrder[rod.fishingRod.rarity] ?: 0
        }.thenByDescending { rod ->
            rod.userFishingRod.durability
        }
    ).first()

    val key = "fishing:fishs:${event.user.id}"
    val cachedAvaibleFishes = Cache.get<Int>(key)
    var avaibleFishes = 20
    if (cachedAvaibleFishes != null)
        avaibleFishes = cachedAvaibleFishes
    else
        Cache.set(key, avaibleFishes, 20, TimeUnit.MINUTES)

    if (avaibleFishes < 1) {
        ComponentBuilder.ContainerBuilder.create()
            .withColor(Colors.DANGER)
            .addText("${Icon.static.get("denied")} | Todos os peixes foram pescados! Espere um pouco para tentar novamente!")
            .reply(event)
        return
    }

    ComponentBuilder.ContainerBuilder.create()
        .withColor(Colors.WARNING)
        .addText("${Icon.static.get("warning")} | Aperte o botão abaixo para iniciar a pescaria!")
        .addRow(ActionRow.of(
            Button.success("fishing/start/${event.user.id}/${betterFishingRod.userFishingRod.id}", "Iniciar")
        ))
        .reply(event)
}