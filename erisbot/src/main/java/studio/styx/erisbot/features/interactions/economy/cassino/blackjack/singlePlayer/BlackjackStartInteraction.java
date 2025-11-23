package studio.styx.erisbot.features.interactions.economy.cassino.blackjack.singlePlayer;

import database.utils.DatabaseUtils;
import games.blackjack.core.singlePlayer.BlackjackErisMood;
import games.blackjack.core.singlePlayer.BlackjackGame;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import shared.Cache;
import shared.Colors;
import shared.utils.CustomIdHelper;
import shared.utils.Utils;
import studio.styx.erisbot.core.ResponderInterface;
import studio.styx.erisbot.generated.tables.records.UserRecord;
import translates.TranslatesObjects;
import utils.ComponentBuilder;
import utils.ContainerRes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class BlackjackStartInteraction implements ResponderInterface {
    @Autowired
    private DSLContext dsl;

    private final ContainerRes res = new ContainerRes();

    @Override
    public String getCustomId() {
        return "blackjack/start/:difficulty/:userId/:amount";
    }

    @Override
    public void execute(ButtonInteractionEvent event) {
        CustomIdHelper customIdHelper = new CustomIdHelper(getCustomId(), event.getCustomId());

        Integer difficulty = customIdHelper.getAsInt("difficulty");
        String userId = customIdHelper.get("userId");
        Double amount = customIdHelper.getAsDouble("amount");

        if (!userId.equals(event.getUser().getId())) {
            res.setColor(Colors.DANGER)
                    .setText("Você não pode usar esse botão!")
                    .setEphemeral()
                    .send(event);
            return;
        }

        event.deferEdit().queue(hook -> {
            UserRecord user = DatabaseUtils.getOrCreateUser(dsl, event.getUser().getId());

            if (user.getMoney().doubleValue() < amount) {
                res.setColor(Colors.DANGER)
                        .setText("You don't have enough money to bet this amount")
                        .edit(hook);
                return;
            }

            var game = new BlackjackGame(
                    Utils.getRandomListValue(List.of(
                            BlackjackErisMood.ANGRY, BlackjackErisMood.CONFUSED,
                            BlackjackErisMood.HAPPY, BlackjackErisMood.SCARED,
                            BlackjackErisMood.SURPRISED, BlackjackErisMood.SAD,
                            BlackjackErisMood.NEUTRAL
                            )),
                    difficulty == null ? 0 : difficulty,
                    amount
            );

            game.startGame();

            Cache.set("blackjack:game:singlePlayer:" + event.getUser().getId(), game);

            studio.styx.erisbot.menus.economy.cassino.blackjack.BlackjackGame menu = new studio.styx.erisbot.menus.economy.cassino.blackjack.BlackjackGame();

            var t = TranslatesObjects.getBlackjack(event.getUserLocale().getLocale());

            menu.setUserId(userId)
                    .setAmount(amount)
                    .setGame(game)
                    .setTraduction(t);

            hook.editOriginalComponents(menu.blackjackGameMenu(event.getUserLocale())).useComponentsV2().queue();
        });
    }

    @Override
    public void execute(EntitySelectInteractionEvent event) {
        CustomIdHelper customIdHelper = new CustomIdHelper(getCustomId(), event.getCustomId());

        String userId = customIdHelper.get("userId");
        Double amount = customIdHelper.getAsDouble("amount");

        if (!userId.equals(event.getUser().getId())) {
            res.setColor(Colors.DANGER)
                    .setText("Você não pode usar esse botão!")
                    .setEphemeral()
                    .send(event);
            return;
        }

        var targetMention = event.getValues().getFirst();
        var user = event.getUser();

        if (user.getId().equals(targetMention.getId())) {
            res.setColor(Colors.DANGER)
                    .setText("Você não pode jogar blackjack contra si mesmo!")
                    .send(event);
            return;
        }
        try {
            var target = event.getGuild().getMemberById(targetMention.getId()).getUser();
            if (target.getId().equals(event.getJDA().getSelfUser().getId())) {
                event.deferEdit().queue(hook -> {
                    UserRecord userData = DatabaseUtils.getOrCreateUser(dsl, event.getUser().getId());

                    if (userData.getMoney().doubleValue() < amount) {
                        res.setColor(Colors.DANGER)
                                .setText("You don't have enough money to bet this amount")
                                .edit(hook);
                        return;
                    }

                    var game = new BlackjackGame(
                            Utils.getRandomListValue(List.of(
                                    BlackjackErisMood.ANGRY, BlackjackErisMood.CONFUSED,
                                    BlackjackErisMood.HAPPY, BlackjackErisMood.SCARED,
                                    BlackjackErisMood.SURPRISED, BlackjackErisMood.SAD,
                                    BlackjackErisMood.NEUTRAL
                            )),
                            4,
                            amount
                    );

                    game.startGame();

                    Cache.set("blackjack:game:singlePlayer:" + event.getUser().getId(), game);

                    studio.styx.erisbot.menus.economy.cassino.blackjack.BlackjackGame menu = new studio.styx.erisbot.menus.economy.cassino.blackjack.BlackjackGame();

                    var t = TranslatesObjects.getBlackjack(event.getUserLocale().getLocale());

                    menu.setUserId(userId)
                            .setAmount(amount)
                            .setGame(game)
                            .setTraduction(t);

                    hook.editOriginalComponents(menu.blackjackGameMenu(event.getUserLocale())).useComponentsV2().queue();
                });
            }
            if (target.isBot()) {
                res.setColor(Colors.DANGER)
                        .setText("Você não pode jogar contra um bot!")
                        .send(event);
                return;
            }

            event.deferEdit().queue(hook -> dsl.transaction(config -> {
                DSLContext tx = config.dsl();

                UserRecord userData = DatabaseUtils.getOrCreateUser(tx, user.getId());
                UserRecord targetData = DatabaseUtils.getOrCreateUser(tx, target.getId());

                if (userData.getMoney().doubleValue() < amount) {
                    res.setColor(Colors.DANGER)
                            .setText("You don't have enough money to bet this amount")
                            .edit(hook);
                    return;
                }

                if (targetData.getMoney().doubleValue() < amount) {
                    res.setColor(Colors.DANGER)
                            .setText("The selected user don't tabe enought money to bet this amount")
                            .edit(hook);
                    return;
                }

                hook.editOriginalComponents(ComponentBuilder.ContainerBuilder.create()
                        .withColor(Colors.PRIMARY)
                        .addText("Wait the selected user accept")
                        .addRow(ActionRow.of(
                                Button.success(Utils.replaceText(
                                        "blackjack/start/other/accept/{userId}/{targetId}/{amount}",
                                        Map.of(
                                                "userId", userId,
                                                "targetId", target.getId(),
                                                "amount", amount.toString()
                                        )
                                ), "Aceitar")
                        )).build()).useComponentsV2().queue();
            }));
        } catch (NullPointerException e) {
            res.setColor(Colors.DANGER)
                    .setText("Esse usuário não está mais no servidor")
                    .send(event);
        }
    }
}
