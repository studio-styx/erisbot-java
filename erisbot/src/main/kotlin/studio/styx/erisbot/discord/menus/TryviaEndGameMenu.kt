package studio.styx.erisbot.discord.menus

import games.tryvia.core.TryviaGame
import net.dv8tion.jda.api.components.MessageTopLevelComponent
import shared.utils.Utils
import utils.ComponentBuilder

fun tryviaEndGameMenu(game: TryviaGame, thumbnail: String): MutableList<MessageTopLevelComponent> {
    val ranking = game.getParticipantsByPointsOrder()

    val result = ranking.mapIndexed { index, player ->
        "**${index + 1}.** - **<@${player.id}>** - **${player.points}** pontos"
    }.joinToString("\n")

    return mutableListOf(
        ComponentBuilder.ContainerBuilder.create()
            .addText("# Fim de jogo")
            .addDivider()
            .addText(Utils.brBuilder(
                if (game.getQuestions().size > 5)
                    "Tivemos várias perguntas até chegarmos aqui"
                    else "Tivemos algumas perguntas até chegarmos aqui",
                "Perguntas difíceis e fáceis, e ${game.getParticipants().size} participantes!",
                "Com isso, o nosso ranking ficou assim:"
            ))
            .addDivider()
            .addSection(thumbnail, result)
            .build()
    )
}