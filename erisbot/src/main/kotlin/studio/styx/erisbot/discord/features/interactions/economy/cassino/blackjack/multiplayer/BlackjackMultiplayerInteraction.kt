package studio.styx.erisbot.discord.features.interactions.economy.cassino.blackjack.multiplayer

import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.interactions.components.button
import games.blackjack.core.multiPlayer.BlackjackMultiplayerEndGameResultType
import games.blackjack.core.multiPlayer.BlackjackMultiplayerGame
import games.blackjack.core.multiPlayer.BlackjackMultiplayerPlayers
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import shared.Cache
import shared.Colors
import shared.utils.CustomIdHelper
import shared.utils.Icon
import shared.utils.MentionUtil
import studio.styx.erisbot.core.dtos.blackjack.BlackjackMultiplayerGameCache
import studio.styx.erisbot.core.interfaces.ResponderInterface
import studio.styx.erisbot.discord.menus.blackjack.blackjackMultiplayerMenu
import studio.styx.erisbot.generated.tables.references.USER
import utils.ComponentBuilder
import java.util.concurrent.TimeUnit

@Component
class BlackjackMultiplayerInteraction : ResponderInterface {
    @Autowired
    lateinit var dsl: DSLContext

    override val customId = "blackjackMultiplayer/game/:action/:userId"

    override suspend fun execute(event: ButtonInteractionEvent) {
        val customIdHelper = CustomIdHelper(customId, event.customId)

        val action = customIdHelper.get("action")!!
        val userId = customIdHelper.get("userId")!!

        val key = "blackjack:game:multiplayer:${event.message.id}"

        val cache = Cache.get<BlackjackMultiplayerGameCache>(key)

        if (cache == null) {
            ComponentBuilder.ContainerBuilder.create()
                .addText("Esse jogo demorou muito, por isso ele foi apagado!")
                .withColor(Colors.DANGER)
                .editOriginal(event)
            return
        }

        val game = cache.game

        if (userId != event.user.id) {
            val container = ComponentBuilder.ContainerBuilder.create()
                .withColor(Colors.DANGER)
                .setEphemeral(true)
            if (event.user.id == game.targetId || event.user.id == game.userId) {
                container.addText("${Icon.static.get("denied")} | Agora é a vez do seu adversário!")
                    .reply(event)
            } else {
                container.addText("${Icon.static.get("denied")} | Você não é parte desse jogo!")
                    .reply(event)
            }
            return
        }

        event.deferEdit().await()

        when(action) {
            "hit" -> {
                val card = game.turn()

                if (card == null) {
                    when(game.turn) {
                        BlackjackMultiplayerPlayers.PLAYER -> targetWin(event, game, "${MentionUtil.userMention(userId)} estourou!")
                        BlackjackMultiplayerPlayers.TARGET -> userWin(event, game, "${MentionUtil.userMention(game.targetId)} estourou!")
                    }
                    Cache.remove(key)
                    return
                }

                game.turnCount++
                game.passCount = 0

                val newUserEvent = if (game.userId == event.user.id) event else cache.userEvent
                val newTargetEvent = if (game.targetId == event.user.id) event else cache.targetEvent

                val newCache = BlackjackMultiplayerGameCache(game, newUserEvent, newTargetEvent)

                setCache(newCache, key)

                val menu = blackjackMultiplayerMenu(game, null)
                event.hook.editOriginalComponents(menu).useComponentsV2().queue()

                sendCardsMessage(newCache)
            }
            "pass" -> {
                if (game.passCount > 4) {
                    val result = game.stop()
                    when(result) {
                        BlackjackMultiplayerEndGameResultType.PLAYER -> userWin(event, game, "${MentionUtil.userMention(userId)} ganhou após vários passes por ter a maior mão")
                        BlackjackMultiplayerEndGameResultType.TARGET -> targetWin(event, game, "${MentionUtil.userMention(game.targetId)} ganhou após vários passes por ter a maior mão")
                        BlackjackMultiplayerEndGameResultType.DRAW -> draw(event, game, "Empate após vários passes, ambos tem a mesma mão")
                    }
                    Cache.remove(key)
                    return
                }

                game.turnCount++
                game.passCount++

                game.turn = when(game.turn) {
                    BlackjackMultiplayerPlayers.PLAYER -> BlackjackMultiplayerPlayers.TARGET
                    BlackjackMultiplayerPlayers.TARGET -> BlackjackMultiplayerPlayers.PLAYER
                }

                val newUserEvent = if (game.userId == event.user.id) event else cache.userEvent
                val newTargetEvent = if (game.targetId == event.user.id) event else cache.targetEvent

                val newCache = BlackjackMultiplayerGameCache(game, newUserEvent, newTargetEvent)

                setCache(newCache, key)

                val menu = blackjackMultiplayerMenu(game, null)
                event.hook.editOriginalComponents(menu).useComponentsV2().queue()

                sendCardsMessage(newCache)
            }
            "stand" -> {
                val targetId = when(game.turn) {
                    BlackjackMultiplayerPlayers.PLAYER -> game.targetId
                    BlackjackMultiplayerPlayers.TARGET -> game.userId
                }

                ComponentBuilder.ContainerBuilder.create()
                    .addText("${MentionUtil.userMention(event.user.id)} Solicitou a parada do jogo! aperte o botão abaixo para aceitar, ou aperte para cancelar")
                    .withColor(Colors.PRIMARY)
                    .addRow(ActionRow.of(
                        Button.success("blackjack/multiplayer/accept/$targetId", "Aceitar"),
                        Button.danger("blackjack/multiplayer/cancel/$targetId", "Cancelar")
                    ))
                    .reply(event)
            }
        }
    }

    private fun sendCardsMessage(cache: BlackjackMultiplayerGameCache) {
        try {
            cache.userEvent?.hook?.sendMessageComponents(ComponentBuilder.ContainerBuilder.create()
                .withColor(Colors.FUCHSIA)
                .addText("Suas cartas: **${cache.game.userCards.joinToString(", ") { c -> "`${c.card}`" }}**, valor: **`${cache.game.calculateHandValue(cache.game.userCards)}`**")
                .build())?.setEphemeral(true)?.useComponentsV2()?.queue()
        } catch (e: Exception) {
            println("Erro ao tentar enviar mensagem das cartas para o usuário: ${cache.game.userId}")
            e.printStackTrace()
        }
        try {
            cache.targetEvent?.hook?.sendMessageComponents(ComponentBuilder.ContainerBuilder.create()
                .withColor(Colors.FUCHSIA)
                .addText("Suas cartas: **${cache.game.targetCards.joinToString(", ") { c -> "`${c.card}`" }}**, valor: **`${cache.game.calculateHandValue(cache.game.targetCards)}`**")
                .build())?.setEphemeral(true)?.useComponentsV2()?.queue()
        } catch (e: Exception) {
            println("Erro ao tentar enviar mensagem das cartas para o usuário: ${cache.game.targetId}")
            e.printStackTrace()
        }
    }

    private fun setCache(cache: BlackjackMultiplayerGameCache, key: String) {
        Cache.set(key, cache, 10, TimeUnit.MINUTES)
    }

    private fun userWin(event: ButtonInteractionEvent, game: BlackjackMultiplayerGame, reason: String) {
        dsl.transaction { config ->
            run {
                val tx = config.dsl()

                tx.update(USER)
                    .set(USER.MONEY, USER.MONEY.add(game.amountAposted))
                    .where(USER.ID.eq(game.userId))
                    .execute()
                tx.update(USER)
                    .set(USER.MONEY, USER.MONEY.sub(game.amountAposted))
                    .where(USER.ID.eq(game.targetId))
                    .execute()

                val menu = blackjackMultiplayerMenu(game, reason)
                event.hook.editOriginalComponents(menu).useComponentsV2().queue()
            }
        }
    }

    private fun targetWin(event: ButtonInteractionEvent, game: BlackjackMultiplayerGame, reason: String) {
        dsl.transaction { config ->
            run {
                val tx = config.dsl()

                tx.update(USER)
                    .set(USER.MONEY, USER.MONEY.add(game.amountAposted))
                    .where(USER.ID.eq(game.targetId))
                    .execute()
                tx.update(USER)
                    .set(USER.MONEY, USER.MONEY.sub(game.amountAposted))
                    .where(USER.ID.eq(game.userId))
                    .execute()

                val menu = blackjackMultiplayerMenu(game, reason)
                event.hook.editOriginalComponents(menu).useComponentsV2().queue()
            }
        }
    }

    private fun draw(event: ButtonInteractionEvent, game: BlackjackMultiplayerGame, reason: String) {
        val menu = blackjackMultiplayerMenu(game, reason)
        event.hook.editOriginalComponents(menu).useComponentsV2().queue()
    }
}