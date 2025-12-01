package studio.styx.erisbot.discord.features.interactions.economy.cassino.blackjack.multiplayer

import dev.minn.jda.ktx.coroutines.await
import games.blackjack.core.multiPlayer.BlackjackMultiplayerEndGameResultType
import games.blackjack.core.multiPlayer.BlackjackMultiplayerGame
import games.blackjack.core.multiPlayer.BlackjackMultiplayerPlayers
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import shared.Cache
import shared.Colors
import shared.utils.CustomIdHelper
import shared.utils.MentionUtil
import studio.styx.erisbot.core.dtos.blackjack.BlackjackMultiplayerGameCache
import studio.styx.erisbot.core.interfaces.ResponderInterface
import studio.styx.erisbot.discord.menus.blackjack.blackjackMultiplayerMenu
import studio.styx.erisbot.generated.tables.references.USER
import utils.ComponentBuilder
import java.util.concurrent.TimeUnit

@Component
class BlackjackMultiplayerStandRequestInteraction : ResponderInterface {
    @Autowired
    lateinit var dsl: DSLContext

    override val customId = "blackjack/multiplayer/:accept/:targetId"

    override suspend fun execute(event: ButtonInteractionEvent) {
        val customIdHelper = CustomIdHelper(customId, event.customId)
        val targetId = customIdHelper.get("targetId")!!

        if (targetId != event.user.id) {
            ComponentBuilder.ContainerBuilder.create()
                .addText("Apenas ${MentionUtil.userMention(targetId)} pode decidir!")
                .setEphemeral(true)
                .reply(event)
            return
        }

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

        val accept = customIdHelper.get("action") == "accept"
        if (accept) {
            val result = game.stop()

            when(result) {
                BlackjackMultiplayerEndGameResultType.PLAYER -> userWin(event, game, "${MentionUtil.userMention(game.userId)} ganhou após terminar o jogo por ter a maior mão")
                BlackjackMultiplayerEndGameResultType.TARGET -> targetWin(event, game, "${MentionUtil.userMention(game.targetId)} ganhou após terminar o jogo por ter a maior mão")
                BlackjackMultiplayerEndGameResultType.DRAW -> draw(event, game, "Empate após terminar o jogo, ambos tem a mesma mão")
            }
            Cache.remove(key)
        } else {
            game.turn = when(game.turn) {
                BlackjackMultiplayerPlayers.PLAYER -> BlackjackMultiplayerPlayers.TARGET
                BlackjackMultiplayerPlayers.TARGET -> BlackjackMultiplayerPlayers.PLAYER
            }

            game.turnCount++

            val newCache = BlackjackMultiplayerGameCache(game, cache.userEvent, event)

            setCache(newCache, key)

            val menu = blackjackMultiplayerMenu(game, null)

            event.editComponents(menu).useComponentsV2().queue()
        }
    }

    private fun setCache(cache: BlackjackMultiplayerGameCache, key: String) {
        Cache.set(key, cache, 10, TimeUnit.MINUTES)
    }

    private suspend fun userWin(event: ButtonInteractionEvent, game: BlackjackMultiplayerGame, reason: String) {
        event.deferEdit().await()

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

    private suspend fun targetWin(event: ButtonInteractionEvent, game: BlackjackMultiplayerGame, reason: String) {
        event.deferEdit().await()

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