package studio.styx.erisbot.discord.features.interactions.economy.cassino.blackjack.singlePlayer

import database.utils.LogManage
import games.blackjack.core.singlePlayer.Action
import games.blackjack.core.singlePlayer.BlackjackEndGameResultType
import games.blackjack.core.singlePlayer.BlackjackGame
import games.blackjack.core.singlePlayer.BlackjackIA
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.interactions.DiscordLocale
import net.dv8tion.jda.api.interactions.InteractionHook
import org.jooq.Configuration
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import shared.Cache
import shared.Colors
import shared.utils.CustomIdHelper
import studio.styx.erisbot.core.interfaces.ResponderInterface
import studio.styx.erisbot.discord.menus.blackjack.BlackjackAction
import studio.styx.erisbot.discord.menus.blackjack.BlackjackGameMenu
import studio.styx.erisbot.generated.tables.references.USER
import translates.TranslatesObjects
import translates.commands.economy.cassino.blackjack.BlackjackTranslateInterface
import utils.ContainerRes
import java.util.concurrent.TimeUnit
import java.util.function.Consumer


@Component
class BlackjackInteraction : ResponderInterface {
    @Autowired
    lateinit var dsl: DSLContext

    override fun getCustomId(): String {
        return "blackjack/game/:action/:userId"
    }

    private val res = ContainerRes()

    override fun execute(event: ButtonInteractionEvent) {
        val customIdHelper = CustomIdHelper(customId, event.customId);
        val action = customIdHelper.get("action")!!
        val userId = customIdHelper.get("userId")!!

        var t = TranslatesObjects.getBlackjack(event.userLocale.locale)

        if (userId != event.user.id) {
            res.setColor(Colors.DANGER)
                .setText("Ei! esse jogo não é seu!")
                .setEphemeral()
                .send(event)
            return
        }

        val game = Cache.get<BlackjackGame>("blackjack:game:singlePlayer:$userId")

        if (game == null) {
            res.setColor(Colors.DANGER)
                .setText("Esse jogo demorou muito, por isso ele foi apagado!")
                .setEphemeral()
                .send(event)
            return
        }

        event.deferEdit().queue(Consumer { hook: InteractionHook -> dsl.transaction { config: Configuration ->
            run {
                val tx = config.dsl()

                val ia = BlackjackIA(game)

                val multiplier = if (game.difficulty <= 1) 1.5 else game.difficulty * 1.5;
                val amount = game.amountAposted * multiplier

                when (action) {
                    "hit" -> {
                        val card = game?.userTurn()

                        if (card == null) {
                            userLose(event, hook, tx, game, ia, t,
                                "User lost a blackjack game and lost ${game.amountAposted} STX. because the user busted.",
                                "busted")
                            return@transaction
                        }

                        game.turnCount++
                        game.passCount = 0
                        val menu = BlackjackGameMenu()
                            .setUserId(event.user.id)
                            .setGame(game)
                            .setAmount(game.amountAposted)
                            .setTraduction(t)
                            .setDisableButtons(true)
                            .setAction(BlackjackAction.THINKING)

                        hook.editOriginalComponents(menu.blackjackGameMenu(event.userLocale)).useComponentsV2().queue()

                        event.jda.gatewayPool.schedule({
                            erisAction(event, hook, tx, game, ia, amount, t)
                        }, 2, TimeUnit.SECONDS)
                    }
                    "pass" -> {
                        if (game.passCount > 3) {
                            val result = game.stop()
                            if (result == BlackjackEndGameResultType.PLAYER) {
                                userWin(
                                    event, hook, tx, game, ia, amount, t,
                                    "User won a blackjack game and earned $amount STX. because the hand value is bigger to dealer after both players passed.",
                                    "both_passed"
                                )
                            } else if (result == BlackjackEndGameResultType.BOT) {
                                userLose(
                                    event, hook, tx, game, ia, t,
                                    "User lost a blackjack game and lost ${game.amountAposted} STX. because the hand value is smaller to dealer after both players passed.",
                                    "both_passed"
                                )
                            } else {
                                draw(event, hook, tx, game, ia, t,
                                    "User pushed a blackjack game after both players passed.",
                                    "both_passed"
                                )
                            }
                            return@transaction;
                        }

                        game.turnCount++
                        game.passCount++

                        val menu = BlackjackGameMenu()
                            .setUserId(event.user.id)
                            .setGame(game)
                            .setAmount(game.amountAposted)
                            .setTraduction(t)
                            .setDisableButtons(true)
                            .setAction(BlackjackAction.THINKING)

                        hook.editOriginalComponents(menu.blackjackGameMenu(event.userLocale)).useComponentsV2().queue()

                        event.jda.gatewayPool.schedule({
                            erisAction(event, hook, tx, game, ia, amount, t)
                        }, 2, TimeUnit.SECONDS)
                    }
                    "stand" -> {
                        val result = game.stop()
                        if (result == BlackjackEndGameResultType.PLAYER) {
                            userWin(
                                event, hook, tx, game, ia, amount, t,
                                "User won a blackjack game and earned $amount STX. because the hand value is bigger to dealer after stand.",
                                "stand"
                            )
                        } else if (result == BlackjackEndGameResultType.BOT) {
                            userLose(
                                event, hook, tx, game, ia, t,
                                "User lost a blackjack game and lost ${game.amountAposted} STX. because the hand value is smaller to dealer after stand.",
                                "stand"
                            )
                        } else {
                            draw(event, hook, tx, game, ia, t,
                                "User pushed a blackjack game after stand.",
                                "stand"
                            )
                        }

                        Cache.remove("blackjack:game:singlePlayer:${event.user.id}")
                        return@transaction;
                    }
                }
                Cache.set("blackjack:game:singlePlayer:${event.user.id}", game)
            }
        }})
    }

    private fun erisAction(
        event: ButtonInteractionEvent,
        hook: InteractionHook,
        tx: DSLContext,
        game: BlackjackGame,
        ia: BlackjackIA,
        amount: Double,
        t: BlackjackTranslateInterface,
    ) {
        val erisAction = ia.decideErisAction();

        val multiplier = if (game.difficulty <= 1) 1.5 else game.difficulty * 1.5;

        when (erisAction.action) {
            Action.HIT -> {
                val card = game.erisTurn(erisAction.card)
                if (card == null) {
                    userWin(event, hook, tx, game, ia, amount, t,
                            "User won a blackjack game and earned ${game.amountAposted * multiplier} STX. beacuse the dealer busted.",
                        "busted"
                    )
                } else {
                    game.turnCount++
                    game.passCount = 0
                    var comentary = when (event.userLocale) {
                        DiscordLocale.PORTUGUESE_BRAZILIAN -> ia.getErisComentary().ptbr
                        DiscordLocale.SPANISH, DiscordLocale.SPANISH_LATAM -> ia.getErisComentary().eses
                        else -> ia.getErisComentary().enus
                    }

                    val menu = BlackjackGameMenu()
                        .setUserId(event.user.id)
                        .setGame(game)
                        .setComentary(comentary)
                        .setAmount(game.amountAposted)
                        .setTraduction(t)
                        .setDisableButtons(false)
                        .setAction(BlackjackAction.HIT)

                    hook.editOriginalComponents(menu.blackjackGameMenu(event.userLocale)).useComponentsV2().queue()
                }
            }
            Action.STAND -> {
                var comentary = when (event.userLocale) {
                    DiscordLocale.PORTUGUESE_BRAZILIAN -> ia.getErisComentary().ptbr
                    DiscordLocale.SPANISH, DiscordLocale.SPANISH_LATAM -> ia.getErisComentary().eses
                    else -> ia.getErisComentary().enus
                }
                game.turnCount++
                game.passCount++

                val menu = BlackjackGameMenu()
                    .setUserId(event.user.id)
                    .setGame(game)
                    .setComentary(comentary)
                    .setAmount(game.amountAposted)
                    .setTraduction(t)
                    .setDisableButtons(false)
                    .setAction(BlackjackAction.PASS)

                hook.editOriginalComponents(menu.blackjackGameMenu(event.userLocale)).useComponentsV2().queue()
            }
        }
        Cache.set("blackjack:game:singlePlayer:${event.user.id}", game)
    }

    private fun userWin(
        event: ButtonInteractionEvent,
        hook: InteractionHook,
        tx: DSLContext,
        game: BlackjackGame,
        ia: BlackjackIA,
        amount: Double,
        t: BlackjackTranslateInterface,
        reason: String,
        cause: String
    ) {
        tx.update(USER)
            .set(USER.MONEY, USER.MONEY.add(amount))
            .where(USER.ID.eq(event.user.id))
            .execute()

        val menu = BlackjackGameMenu()
            .setUserId(event.user.id)
            .setWins(BlackjackEndGameResultType.PLAYER)
            .setGame(game)
            .setComentary(
                when (event.userLocale){
                    DiscordLocale.PORTUGUESE_BRAZILIAN -> ia.getErisComentary(BlackjackEndGameResultType.PLAYER).ptbr
                    DiscordLocale.SPANISH, DiscordLocale.SPANISH_LATAM -> ia.getErisComentary(BlackjackEndGameResultType.PLAYER).eses
                    else -> ia.getErisComentary(BlackjackEndGameResultType.PLAYER).enus
                }
            )
            .setAmount(game.amountAposted)
            .setTraduction(t)
            .setDisableButtons(true)
            .setAction(BlackjackAction.THINKING)

        hook.editOriginalComponents(menu.blackjackGameMenu(event.userLocale)).useComponentsV2().queue()

        Cache.remove("blackjack:game:singlePlayer:${event.user.id}")

        LogManage.CreateLog.create()
            .setUserId(event.user.id)
            .setLevel(3)
            .setMessage(reason)
            .setTags(listOf("blackjack", "game", game.difficulty.toString(), "sum", "wins", cause))
            .insert(tx)
    }

    private fun userLose(
        event: ButtonInteractionEvent,
        hook: InteractionHook,
        tx: DSLContext,
        game: BlackjackGame,
        ia: BlackjackIA,
        t: BlackjackTranslateInterface,
        reason: String,
        cause: String
    ) {
        tx.update(USER)
            .set(USER.MONEY, USER.MONEY.sub(game.amountAposted))
            .where(USER.ID.eq(event.user.id))
            .execute()

        val menu = BlackjackGameMenu()
            .setUserId(event.user.id)
            .setWins(BlackjackEndGameResultType.BOT)
            .setGame(game)
            .setComentary(
                when (event.userLocale){
                    DiscordLocale.PORTUGUESE_BRAZILIAN -> ia.getErisComentary(BlackjackEndGameResultType.BOT).ptbr
                    DiscordLocale.SPANISH, DiscordLocale.SPANISH_LATAM -> ia.getErisComentary(BlackjackEndGameResultType.BOT).eses
                    else -> ia.getErisComentary(BlackjackEndGameResultType.BOT).enus
                }
            )
            .setAmount(game.amountAposted)
            .setTraduction(t)
            .setDisableButtons(true)
            .setAction(BlackjackAction.THINKING)

        hook.editOriginalComponents(menu.blackjackGameMenu(event.userLocale)).useComponentsV2().queue()
        Cache.remove("blackjack:game:singlePlayer:${event.user.id}")

        LogManage.CreateLog.create()
            .setUserId(event.user.id)
            .setLevel(3)
            .setMessage(reason)
            .setTags(listOf("blackjack", "game", game.difficulty.toString(), "sub", "lose", cause))
            .insert(tx)
    }

    private fun draw(
        event: ButtonInteractionEvent,
        hook: InteractionHook,
        tx: DSLContext,
        game: BlackjackGame,
        ia: BlackjackIA,
        t: BlackjackTranslateInterface,
        reason: String,
        cause: String
    ) {
        val menu = BlackjackGameMenu()
            .setUserId(event.user.id)
            .setWins(BlackjackEndGameResultType.DRAW)
            .setGame(game)
            .setComentary(
                when (event.userLocale){
                    DiscordLocale.PORTUGUESE_BRAZILIAN -> ia.getErisComentary(BlackjackEndGameResultType.DRAW).ptbr
                    DiscordLocale.SPANISH, DiscordLocale.SPANISH_LATAM -> ia.getErisComentary(BlackjackEndGameResultType.DRAW).eses
                    else -> ia.getErisComentary(BlackjackEndGameResultType.DRAW).enus
                }
            )
            .setAmount(game.amountAposted)
            .setTraduction(t)
            .setDisableButtons(true)
            .setAction(BlackjackAction.THINKING)

        hook.editOriginalComponents(menu.blackjackGameMenu(event.userLocale)).useComponentsV2().queue()
        Cache.remove("blackjack:game:singlePlayer:${event.user.id}")

        LogManage.CreateLog.create()
            .setUserId(event.user.id)
            .setLevel(3)
            .setMessage(reason)
            .setTags(listOf("blackjack", "game", game.difficulty.toString(), "lose", cause))
            .insert(tx)
    }
}