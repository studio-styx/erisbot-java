package games.fish

import emojis.EmojiLoader
import net.dv8tion.jda.api.components.MessageTopLevelComponent
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.components.buttons.ButtonStyle
import net.dv8tion.jda.api.components.textdisplay.TextDisplay
import net.dv8tion.jda.api.entities.emoji.Emoji
import shared.Colors
import shared.utils.Utils
import utils.ComponentBuilder
import java.time.LocalDateTime

fun fishingMenu(userId: String, rodId: Int, round: Int = 1, button: Int? = null, disableButtons: Boolean = false): MutableList<MessageTopLevelComponent> {
    val buttons: MutableList<Button> = mutableListOf()

    for (i in 1..5) {
        buttons.add(
            Button.of(
                if (button == i) ButtonStyle.SUCCESS else ButtonStyle.SECONDARY,
                "fishing/fish/$i/${userId}/${LocalDateTime.now().nano}/${button == i}",
                "Pescar",
                Emoji.fromCustom("pinkfish", EmojiLoader.emojis.static["pinkfish"]!!.toLong(), false)
            )
        )
    }

    return mutableListOf(
        ComponentBuilder.ContainerBuilder.create()
            .withColor(Colors.AZOXO)
            .add(TextDisplay.of("## Pescaria | $round").withUniqueId(rodId))
            .addDivider()
            .addText(Utils.brBuilder(
                "Pesque peixes e venda-os para ganhar dinheiro!",
                "Você deve esperar até um dos botões ficar **verde** para clicar nele.",
                "Quanto mais rápido você clicar, melhor será o peixe que você pegará.",
                "Você pode comprar varas de pescar melhores na loja para aumentar suas chances de pegar peixes melhores."
            ))
            .addDivider()
            .addRow(ActionRow.of(buttons))
            .build()
    )
}