package studio.styx.erisbot.discord.features.interactions.economy.cassino.blackjack

import database.utils.DatabaseUtils
import dev.minn.jda.ktx.coroutines.await
import games.blackjack.core.singlePlayer.BlackjackErisMood
import games.blackjack.core.singlePlayer.BlackjackGame
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.entities.IMentionable
import net.dv8tion.jda.api.entities.Member
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
import shared.utils.MentionUtil
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

    override val customId = "blackjack/start/:difficulty/:userId/:amount"

    override suspend fun execute(event: ButtonInteractionEvent) {
        val customIdHelper = CustomIdHelper(customId, event.customId)

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

    override suspend fun execute(event: EntitySelectInteractionEvent) {
        val customIdHelper = CustomIdHelper(customId, event.getCustomId())

        val userId = customIdHelper.get("userId")
        val amount = customIdHelper.getAsDouble("amount")

        if (userId != event.user.id) {
            res.setColor(Colors.DANGER)
                .setText("Você não pode usar esse botão!")
                .setEphemeral()
                .send(event)
            return
        }

        if (event.values.isEmpty()) {
            res.setColor(Colors.DANGER)
                .setText("Nenhum usuário selecionado!")
                .setEphemeral(true)
                .send(event)
            return
        }

        val targetMention: IMentionable = event.values.first()
        val user = event.user

        if (user.id == targetMention.id) {
            res.setColor(Colors.DANGER)
                .setText("Você não pode jogar blackjack contra si mesmo!")
                .setEphemeral(true)
                .send(event)
            return
        }

        val guild = event.guild
        if (guild == null) {
            res.setColor(Colors.DANGER)
                .setText("Este comando só pode ser usado em um servidor!")
                .setEphemeral(true)
                .send(event)
            return
        }

        // Buscar membro de forma segura

        var targetMember: Member?

        targetMember = guild.getMemberById(targetMention.id)

        if (targetMember == null) {
            targetMember = guild.retrieveMemberById(targetMention.id).await()
        }

        if (targetMember == null) {
            res.setColor(Colors.DANGER)
                .setText("Este usuário não está mais no servidor!")
                .setEphemeral(true)
                .send(event)
            return
        }

        val target = targetMember.user

        if (target.id == event.jda.selfUser.id) {
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
                .setEphemeral(true)
                .send(event)
            return
        }

        event.deferEdit().await()
        dsl.transaction { config: Configuration ->
            val tx = config.dsl()
            val userData = DatabaseUtils.getOrCreateUser(tx, user.id)
            val targetData = DatabaseUtils.getOrCreateUser(tx, target.id)

            if (userData.money!!.toDouble() < amount!!) {
                val container = res.setColor(Colors.DANGER)
                    .setText("You don't have enough money to bet this amount")
                    .build()
                event.hook.sendMessageComponents(container).setEphemeral(true).useComponentsV2().queue()
                return@transaction
            }

            if (targetData.money!!.toDouble() < amount) {
                val container = res.setColor(Colors.DANGER)
                    .setText("The selected user don't have enought money to bet this amount")
                    .build()

                event.hook.sendMessageComponents(container).setEphemeral(true).useComponentsV2().queue()
                return@transaction
            }
            event.hook.editOriginalComponents(
                ComponentBuilder.ContainerBuilder.create()
                    .withColor(Colors.PRIMARY)
                    .addText("Wait the ${MentionUtil.userMention(target.id)} user accept")
                    .addRow(
                        ActionRow.of(
                            Button.success(
                                "blackjackMultiplayer/start/accept/${user.id}/${target.id}/$amount", "Aceitar"
                            )
                        )
                    ).build()
            ).useComponentsV2().queue()
        }
    }
}