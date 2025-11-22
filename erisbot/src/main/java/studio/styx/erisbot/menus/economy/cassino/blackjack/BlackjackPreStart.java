package studio.styx.erisbot.menus.economy.cassino.blackjack;

import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import org.jetbrains.annotations.NotNull;
import shared.Colors;
import shared.utils.Utils;
import translates.commands.economy.cassino.blackjack.BlackjackPreStartInterface;
import utils.ComponentBuilder;

import java.util.List;
import java.util.Map;

public class BlackjackPreStart {
    public List<MessageTopLevelComponent> blackjackContainer(String userId, @NotNull BlackjackPreStartInterface t, int games, @NotNull Double amount) {
        EntitySelectMenu usersMenu = EntitySelectMenu.create(Utils.replaceText(
                    "blackjack/start/other/{userId}/{amount}",
                    Map.of(
                            "userId", userId,
                            "amount", amount.toString()
                    )
                ), EntitySelectMenu.SelectTarget.USER)
                .setPlaceholder(t.otherPlayer())
                .setRequiredRange(1, 1)
                .build();

        Container container = ComponentBuilder.ContainerBuilder.create()
                .withColor(Colors.DANGER)
                .addText(t.title(games))
                .addDivider(false)
                .addRow(ActionRow.of(
                        Button.secondary(Utils.replaceText(
                                "blackjack/start/0/{userId}/{amount}",
                                Map.of(
                                        "userId", userId,
                                        "amount", amount.toString()
                                )
                        ),
                                t.classicDealer()
                        ).withDisabled(games >= 4),
                        Button.success(Utils.replaceText(
                                        "blackjack/start/1/{userId}/{amount}",
                                        Map.of(
                                                "userId", userId,
                                                "amount", amount.toString()
                                        )
                                ),
                                t.erisEasy()
                        ).withDisabled(games >= 4),
                        Button.success(Utils.replaceText(
                                        "blackjack/start/2/{userId}/{amount}",
                                        Map.of(
                                                "userId", userId,
                                                "amount", amount.toString()
                                        )
                                ),
                                t.erisNormal()
                        ).withDisabled(games >= 6),
                        Button.danger(Utils.replaceText(
                                        "blackjack/start/3/{userId}/{amount}",
                                        Map.of(
                                                "userId", userId,
                                                "amount", amount.toString()
                                        )
                                ),
                                t.erisHard()
                        ).withDisabled(games >= 8),
                        Button.danger(Utils.replaceText(
                                        "blackjack/start/0/{userId}/{amount}",
                                        Map.of(
                                                "userId", userId,
                                                "amount", amount.toString()
                                        )
                                ),
                                t.erisNightmare()
                        ).withDisabled(games >= 14)
                ))
                .addRow(ActionRow.of(usersMenu))
                .build();

        return List.of(container);
    }
}
