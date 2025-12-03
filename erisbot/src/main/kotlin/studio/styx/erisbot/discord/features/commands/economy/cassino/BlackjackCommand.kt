package studio.styx.erisbot.discord.features.commands.economy.cassino

import database.extensions.getOrCreateUser
import games.blackjack.core.singlePlayer.BlackjackGame
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import shared.Cache.get
import shared.Colors
import shared.utils.Icon
import studio.styx.erisbot.core.interfaces.CommandInterface
import studio.styx.erisbot.discord.menus.blackjack.BlackjackPreStart
import translates.TranslatesObjects.getBlackjackPreStart
import utils.ComponentBuilder.ContainerBuilder.Companion.create


@Component
class BlackjackCommand : CommandInterface {
    @Autowired
    lateinit var dsl: DSLContext

    override fun getSlashCommandData(): SlashCommandData {
        val amount = OptionData(OptionType.NUMBER, "amount", "valor a apostar", true)
            .setMinValue(50)

        return Commands.slash("blackjack", "jogue uma partida de blackjack")
            .addOptions(amount)
    }

    override suspend fun execute(event: SlashCommandInteractionEvent) {
        val game = get<BlackjackGame?>("blackjack:game:singlePlayer:${event.user.id}")

        if (game != null) {
            create()
                .withColor(Colors.DANGER)
                .addText("${Icon.static.get("denied")} | You are already in a game, press the red button to delete the games")
                .addRow(
                    ActionRow.of(
                        Button.danger("blackjack/delete/delete", "Delete")
                    )
                )
                .setEphemeral(true)
                .reply(event)
            return
        }

        val t = getBlackjackPreStart(event.getUserLocale().getLocale())

        var amount = event.getOption("amount")!!.getAsDouble()
        val user = dsl.getOrCreateUser(event.user.id)
        if (user.money!!.toDouble() < amount) {
            amount = user.money!!.toDouble()
        }
        if (amount < 50) {
            create()
                .withColor(Colors.DANGER)
                .addText(t.notEnoughMoney())
                .setEphemeral(true)
                .reply(event)
            return
        }

        val menu = BlackjackPreStart()

        val games = get<Int?>("blackjack:games:amount:" + event.getUser().getId())

        event.replyComponents(
            menu.blackjackContainer(
                event.getUser().getId(), t,
                games ?: 0, amount
            )
        ).useComponentsV2().queue()
    }
}
