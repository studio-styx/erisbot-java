package studio.styx.erisbot.discord.features.interactions.economy.cassino.blackjack.multiplayer

import database.extensions.getOrCreateUser
import dev.minn.jda.ktx.coroutines.await
import games.blackjack.core.multiPlayer.BlackjackMultiplayerGame
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
import translates.TranslatesObjects
import utils.ComponentBuilder
import java.util.concurrent.TimeUnit

@Component
class StartBlackjackMultiplayerGame : ResponderInterface {
    @Autowired
    lateinit var dsl: DSLContext

    override val customId = "blackjackMultiplayer/start/accept/:userId/:targetId/:amount"

    override suspend fun execute(event: ButtonInteractionEvent) {
        val customIdHelper = CustomIdHelper(customId, event.customId)
        val userId = customIdHelper.get("userId")!!
        val targetId = customIdHelper.get("targetId")!!
        val amount = customIdHelper.getAsDouble("amount")!!

        if (userId == event.user.id) {
            ComponentBuilder.ContainerBuilder.create()
                .addText("${Icon.static.get("denied")} | Você cancelou o pedido!")
                .withColor(Colors.DANGER)
                .editOriginal(event)
            return
        }

        if (targetId != event.user.id) {
            ComponentBuilder.ContainerBuilder.create()
                .addText("${Icon.static.get("denied")} | Você não é ${MentionUtil.userMention(targetId)} para aceitar esse pedido de jogo!")
                .withColor(Colors.DANGER)
                .setEphemeral(true)
                .reply(event)
            return
        }

        event.deferEdit().await()
        ComponentBuilder.ContainerBuilder.create()
            .addText("${Icon.static.get("waiting_white")} | processando jogo...")
            .withColor(Colors.YELLOW)
            .editOriginal(event)

        dsl.transaction { config ->
            run {
                val tx = config.dsl()

                val userData = dsl.getOrCreateUser(userId)
                val targetData = dsl.getOrCreateUser(targetId)

                val errors = mutableListOf<String>()

                if (userData.money!!.toDouble() < amount) {
                    errors.add("O criador do jogo não tem dinheiro suficiente para essa aposta")
                }
                if (targetData.money!!.toDouble() < amount) {
                    errors.add("O usuário selecionado não tem dinheiro suficiente para essa aposta")
                }
                if (errors.isNotEmpty()) {
                    ComponentBuilder.ContainerBuilder.create()
                        .addText("${Icon.static.get("denied")} | ${errors.joinToString("\n")}")
                        .withColor(Colors.DANGER)
                        .editOriginal(event)
                    return@transaction
                }

                val game = BlackjackMultiplayerGame(userId, targetId, amount)
                game.t = TranslatesObjects.getBlackjackMultiplayer(event.userLocale.locale)
                game.startGame()

                val menu = blackjackMultiplayerMenu(game, null)

                event.hook.editOriginalComponents(menu).useComponentsV2().queue()

                Cache.set("blackjack:game:multiplayer:${event.message.id}", BlackjackMultiplayerGameCache(
                    game,
                    null,
                    event
                ), 10, TimeUnit.MINUTES)
            }
        }
    }
}