package studio.styx.erisbot.discord.features.commands.economy

import database.extensions.getOrCreateUser
import database.utils.LogManage
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.DiscordLocale
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import shared.Colors
import shared.utils.Utils.getRandomListValue
import studio.styx.erisbot.core.interfaces.CommandInterface
import translates.TranslatesObjects.getBalance
import utils.ComponentBuilder
import java.math.BigDecimal
import java.util.List


@Component
class Balance : CommandInterface {
    @Autowired
    lateinit var dsl: DSLContext

    override suspend fun execute(event: SlashCommandInteractionEvent) {
        val userOption = event.getOption("user")
        val targetUser = userOption?.asUser ?: event.user

        val t = getBalance(event.userLocale.locale)

        if (targetUser.id == event.jda.selfUser.id) {
            ComponentBuilder.ContainerBuilder.create()
                .addText(t.erisMoney)
                .withColor(Colors.DANGER)
        }

        if (targetUser.isBot) {
            ComponentBuilder.ContainerBuilder.create()
                .addText(t.botMoney)
                .withColor(Colors.DANGER)
                .setEphemeral(true)
                .reply(event)
            return
        }

        val userId = targetUser.id
        val userRecord = dsl.getOrCreateUser(userId)
        val money = if (userRecord.money != null) userRecord.money else BigDecimal.ZERO

        val replyMessage = t.message(money!!.toDouble(), userId)

        ComponentBuilder.ContainerBuilder.create()
            .addText(getRandomListValue<String?>(replyMessage))
            .withColor(Colors.FUCHSIA)
            .disableMentions()
            .reply(event)

        LogManage.CreateLog.create()
            .setMessage(String.format(t.log(userId, targetUser.id)))
            .setLevel(1)
            .setUserId(userId)
            .setTags(listOf("balance", "economy", "view", "target:" + targetUser.id))
            .insert(dsl)
    }

    override fun getSlashCommandData(): SlashCommandData {
        return Commands.slash("balance", "💳 ✦ Check your balance or another user's balance")
            .addOption(OptionType.USER, "user", "👤 ✦ User to check balance", false)
            .setNameLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "usuário")
            .setDescriptionLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "👤 ✦ O usuário para verificar o saldo")
            .setNameLocalization(DiscordLocale.SPANISH, "usuario")
            .setDescriptionLocalization(DiscordLocale.SPANISH, "👤 ✦ El usuario para verificar el saldo")
            .setNameLocalization(DiscordLocale.SPANISH_LATAM, "usuario")
            .setDescriptionLocalization(DiscordLocale.SPANISH_LATAM, "👤 ✦ El usuario para verificar el saldo")
            .setNameLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "saldo")
            .setDescriptionLocalization(
                DiscordLocale.PORTUGUESE_BRAZILIAN,
                "💳 ✦ Verifica o seu saldo ou o de outro usuário"
            )
            .setNameLocalization(DiscordLocale.SPANISH, "saldo")
            .setDescriptionLocalization(DiscordLocale.SPANISH, "💳 ✦ Verifica tu saldo o el de otro usuario")
            .setNameLocalization(DiscordLocale.SPANISH_LATAM, "saldo")
            .setDescriptionLocalization(DiscordLocale.SPANISH_LATAM, "💳 ✦ Verifica tu saldo o el de otro usuario")
            .setNameLocalization(DiscordLocale.ENGLISH_US, "balance")
            .setDescriptionLocalization(DiscordLocale.ENGLISH_US, "💳 ✦ Check your balance or another user's balance")
    }
}