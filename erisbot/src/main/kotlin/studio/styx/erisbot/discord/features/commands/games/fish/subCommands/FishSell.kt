package studio.styx.erisbot.discord.features.commands.games.fish.subCommands

import dev.minn.jda.ktx.coroutines.await
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import org.jooq.DSLContext
import shared.Colors
import shared.utils.Icon
import shared.utils.Utils
import studio.styx.erisbot.generated.tables.references.FISH
import studio.styx.erisbot.generated.tables.references.USER
import studio.styx.erisbot.generated.tables.references.USERFISH
import utils.ComponentBuilder

suspend fun fishSellCommand(event: SlashCommandInteractionEvent, dsl: DSLContext) {
    val input = event.getOption("id")?.asString ?: return run {
        ComponentBuilder.ContainerBuilder.create()
            .withColor(Colors.DANGER)
            .addText("${Icon.static.get("error")} | Nenhum peixe foi informado!")
            .setEphemeral(true)
            .reply(event)
    }

    when {
        input == "all" -> {
            sellFishes(event, dsl)
        }
        input.toIntOrNull() != null -> {
            val id = input.toInt()
            sellFishes(event, dsl, id)
        }
        else -> {
            ComponentBuilder.ContainerBuilder.create()
                .withColor(Colors.DANGER)
                .addText("${Icon.static.get("error")} | Informe um peixe corretamente!")
                .setEphemeral(true)
                .reply(event)
        }
    }
}

private suspend fun sellFishes(
    event: SlashCommandInteractionEvent,
    dsl: DSLContext,
    fishId: Int? = null // null = vender todos
) {
    event.deferReply().await()

    dsl.transaction { config ->
        val tx = config.dsl()

        val query = tx.select(USERFISH.asterisk())
            .from(USERFISH)
            .innerJoin(FISH).on(FISH.ID.eq(USERFISH.FISHID))
            .where(USERFISH.USERID.eq(event.user.id))

        // Adiciona filtro por ID se fornecido
        if (fishId != null) {
            query.and(USERFISH.FISHID.eq(fishId))
        }

        val fishesToSell = query.fetch()

        if (fishesToSell.isEmpty()) {
            ComponentBuilder.ContainerBuilder.create()
                .withColor(Colors.DANGER)
                .addText("${Icon.static.get("error")} | Nenhum peixe foi encontrado para vender!")
                .reply(event)
            return@transaction
        }

        var winAmount = 0.0
        fishesToSell.forEach { f ->
            winAmount += f.get(FISH.PRICE)!!
        }

        // Deleta os peixes vendidos
        val deleteQuery = tx.deleteFrom(USERFISH)
            .where(USERFISH.USERID.eq(event.user.id))

        if (fishId != null) {
            deleteQuery.and(USERFISH.FISHID.eq(fishId))
        }

        deleteQuery.execute()

        // Atualiza o dinheiro do usuário
        tx.update(USER)
            .set(USER.MONEY, USER.MONEY.add(winAmount))
            .where(USER.ID.eq(event.user.id))
            .execute()

        ComponentBuilder.ContainerBuilder.create()
            .withColor(Colors.SUCCESS)
            .addText("${Icon.static.get("success")} | Você vendeu **${fishesToSell.size}** peixes por **${Utils.formatNumber(winAmount)}** stx!")
            .reply(event)
    }
}