package menus.football

import net.dv8tion.jda.api.components.MessageTopLevelComponent
import utils.ComponentBuilder

fun footballTermsAndConditionsMenu(userId: String): MutableList<MessageTopLevelComponent> {
    return mutableListOf(
        ComponentBuilder.ContainerBuilder.create()
            .addText()
    )
}