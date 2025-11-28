package studio.styx.erisbot.discord.menus.blackjack

import net.dv8tion.jda.api.components.MessageTopLevelComponent
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.components.selections.EntitySelectMenu
import shared.Colors
import shared.utils.Utils.replaceText
import translates.commands.economy.cassino.blackjack.BlackjackPreStartInterface
import utils.ComponentBuilder.ContainerBuilder.Companion.create
import java.util.List
import java.util.Map

class BlackjackPreStart {
    fun blackjackContainer(
        userId: String,
        t: BlackjackPreStartInterface,
        games: Int,
        amount: Double
    ): MutableList<MessageTopLevelComponent?> {
        val usersMenu = EntitySelectMenu.create(
            replaceText(
                "blackjack/start/other/{userId}/{amount}",
                Map.of<String, String>(
                    "userId", userId,
                    "amount", amount.toString()
                )
            ), EntitySelectMenu.SelectTarget.USER
        )
            .setPlaceholder(t.otherPlayer())
            .setRequiredRange(1, 1)
            .build()

        val container = create()
            .withColor(Colors.DANGER)
            .addText(t.title(games))
            .addDivider(false)
            .addRow(
                ActionRow.of(
                    Button.secondary(
                        replaceText(
                            "blackjack/start/0/{userId}/{amount}",
                            Map.of<String, String>(
                                "userId", userId,
                                "amount", amount.toString()
                            )
                        ),
                        t.classicDealer()
                    ).withDisabled(games >= 4),
                    Button.success(
                        replaceText(
                            "blackjack/start/1/{userId}/{amount}",
                            Map.of<String, String>(
                                "userId", userId,
                                "amount", amount.toString()
                            )
                        ),
                        t.erisEasy()
                    ).withDisabled(games >= 4),
                    Button.success(
                        replaceText(
                            "blackjack/start/2/{userId}/{amount}",
                            Map.of<String, String>(
                                "userId", userId,
                                "amount", amount.toString()
                            )
                        ),
                        t.erisNormal()
                    ).withDisabled(games >= 6),
                    Button.danger(
                        replaceText(
                            "blackjack/start/3/{userId}/{amount}",
                            Map.of<String, String>(
                                "userId", userId,
                                "amount", amount.toString()
                            )
                        ),
                        t.erisHard()
                    ).withDisabled(games >= 8),
                    Button.danger(
                        replaceText(
                            "blackjack/start/4/{userId}/{amount}",
                            Map.of<String, String>(
                                "userId", userId,
                                "amount", amount.toString()
                            )
                        ),
                        t.erisNightmare()
                    ).withDisabled(games >= 14)
                )
            )
            .addRow(ActionRow.of(usersMenu))
            .build()

        return List.of<MessageTopLevelComponent?>(container)
    }
}
