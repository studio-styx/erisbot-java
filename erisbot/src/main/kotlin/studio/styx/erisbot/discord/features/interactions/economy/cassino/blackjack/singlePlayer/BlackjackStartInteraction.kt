package studio.styx.erisbot.discord.features.interactions.economy.cassino.blackjack.singlePlayer

import database.utils.DatabaseUtils
import games.blackjack.core.singlePlayer.BlackjackErisMood
import games.blackjack.core.singlePlayer.BlackjackGame
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.entities.IMentionable
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionHook
import org.jooq.Configuration
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import shared.Cache
import shared.Colors
import shared.utils.CustomIdHelper
import shared.utils.Utils
import studio.styx.erisbot.core.interfaces.ResponderInterface
import studio.styx.erisbot.discord.menus.blackjack.BlackjackAction
import studio.styx.erisbot.discord.menus.blackjack.BlackjackGameMenu
import translates.TranslatesObjects
import utils.ComponentBuilder
import utils.ContainerRes
import java.util.Map
import java.util.function.Consumer

@Component
class BlackjackStartInteraction : ResponderInterface {
    @Autowired
    lateinit var dsl: DSLContext

    private val res = ContainerRes()

    override fun getCustomId(): String {
        return "blackjack/start/:difficulty/:userId/:amount"
    }

    override fun execute(event: ButtonInteractionEvent) {
        val customIdHelper = CustomIdHelper(getCustomId(), event.getCustomId())

        val difficulty = customIdHelper.getAsInt("difficulty")
        val userId = customIdHelper.get("userId")
        val amount = customIdHelper.getAsDouble("amount")

        if (userId != event.getUser().getId()) {
            res.setColor(Colors.DANGER)
                .setText("Você não pode usar esse botão!")
                .setEphemeral()
                .send(event)
            return
        }

        event.deferEdit().queue(Consumer queue@{ hook: InteractionHook ->
            val user = DatabaseUtils.getOrCreateUser(dsl, event.getUser().getId())
            if (user.money!!.toDouble() < amount!!) {
                res.setColor(Colors.DANGER)
                    .setText("You don't have enough money to bet this amount")
                    .edit(hook)
                return@queue
            }

            val game = BlackjackGame(
                Utils.getRandomListValue(
                    listOf(
                        BlackjackErisMood.ANGRY, BlackjackErisMood.CONFUSED,
                        BlackjackErisMood.HAPPY, BlackjackErisMood.SCARED,
                        BlackjackErisMood.SURPRISED, BlackjackErisMood.SAD,
                        BlackjackErisMood.NEUTRAL
                    )
                ),
                if (difficulty == null) 0 else difficulty,
                amount
            )

            game.startGame()

            Cache.set("blackjack:game:singlePlayer:" + event.getUser().getId(), game)

            val menu = BlackjackGameMenu()

            val t = TranslatesObjects.getBlackjack(event.getUserLocale().getLocale())

            menu.setUserId(userId)
                .setAmount(amount)
                .setGame(game)
                .setTraduction(t)
                .setAction(BlackjackAction.THINKING)
            hook.editOriginalComponents(menu.blackjackGameMenu(event.getUserLocale())).useComponentsV2().queue()
        })
    }

    override fun execute(event: EntitySelectInteractionEvent) {
        val customIdHelper = CustomIdHelper(getCustomId(), event.getCustomId())

        val userId = customIdHelper.get("userId")
        val amount = customIdHelper.getAsDouble("amount")

        if (userId != event.getUser().getId()) {
            res.setColor(Colors.DANGER)
                .setText("Você não pode usar esse botão!")
                .setEphemeral()
                .send(event)
            return
        }

        val targetMention: IMentionable = event.values.first()
        val user = event.user

        if (user.id == targetMention.id) {
            res.setColor(Colors.DANGER)
                .setText("Você não pode jogar blackjack contra si mesmo!")
                .send(event)
            return
        }
        try {
            val target = event.guild!!.getMemberById(targetMention.id)!!.user
            if (target.getId() == event.getJDA().getSelfUser().getId()) {
                event.deferEdit().queue(Consumer queue@{ hook: InteractionHook ->
                    val userData = DatabaseUtils.getOrCreateUser(dsl, event.getUser().getId())
                    if (userData.money!!.toDouble() < amount!!) {
                        res.setColor(Colors.DANGER)
                            .setText("You don't have enough money to bet this amount")
                            .edit(hook)
                        return@queue
                    }

                    val game = BlackjackGame(
                        Utils.getRandomListValue(
                            listOf(
                                BlackjackErisMood.ANGRY, BlackjackErisMood.CONFUSED,
                                BlackjackErisMood.HAPPY, BlackjackErisMood.SCARED,
                                BlackjackErisMood.SURPRISED, BlackjackErisMood.SAD,
                                BlackjackErisMood.NEUTRAL
                            )
                        ),
                        4,
                        amount
                    )

                    game.startGame()

                    Cache.set("blackjack:game:singlePlayer:" + event.getUser().getId(), game)

                    val menu = BlackjackGameMenu()

                    val t = TranslatesObjects.getBlackjack(event.getUserLocale().getLocale())

                    menu.setUserId(userId)
                        .setAmount(amount)
                        .setGame(game)
                        .setTraduction(t)
                        .setAction(BlackjackAction.THINKING)
                    hook.editOriginalComponents(menu.blackjackGameMenu(event.getUserLocale())).useComponentsV2()
                        .queue()
                })
            }
            if (target.isBot) {
                res.setColor(Colors.DANGER)
                    .setText("Você não pode jogar contra um bot!")
                    .send(event)
                return
            }

            event.deferEdit().queue(Consumer { hook: InteractionHook ->
                dsl.transaction { config: Configuration ->
                    val tx = config.dsl()
                    val userData = DatabaseUtils.getOrCreateUser(tx, user.getId())
                    val targetData = DatabaseUtils.getOrCreateUser(tx, target.getId())

                    if (userData.money!!.toDouble() < amount!!) {
                        res.setColor(Colors.DANGER)
                            .setText("You don't have enough money to bet this amount")
                            .edit(hook)
                        return@transaction
                    }

                    if (targetData.money!!.toDouble() < amount) {
                        res.setColor(Colors.DANGER)
                            .setText("The selected user don't tabe enought money to bet this amount")
                            .edit(hook)
                        return@transaction
                    }
                    hook.editOriginalComponents(
                        ComponentBuilder.ContainerBuilder.create()
                            .withColor(Colors.PRIMARY)
                            .addText("Wait the selected user accept")
                            .addRow(
                                ActionRow.of(
                                    Button.success(
                                        Utils.replaceText(
                                            "blackjack/start/other/accept/{userId}/{targetId}/{amount}",
                                            Map.of<String, String>(
                                                "userId", userId,
                                                "targetId", target.getId(),
                                                "amount", amount.toString()
                                            )
                                        ), "Aceitar"
                                    )
                                )
                            ).build()
                    ).useComponentsV2().queue()
                }
            })
        } catch (e: NullPointerException) {
            res.setColor(Colors.DANGER)
                .setText("Esse usuário não está mais no servidor")
                .send(event)
        }
    }
}