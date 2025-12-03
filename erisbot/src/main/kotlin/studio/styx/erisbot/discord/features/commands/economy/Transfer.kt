package studio.styx.erisbot.discord.features.commands.economy

import database.extensions.getOrCreateUser
import database.utils.LogManage
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.components.selections.EntitySelectMenu
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.DiscordLocale
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import org.jooq.Configuration
import org.jooq.DSLContext
import org.jooq.TransactionalRunnable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import shared.Colors
import shared.utils.GenderUnknown
import shared.utils.Utils.getNameGender
import studio.styx.erisbot.core.interfaces.CommandInterface
import studio.styx.erisbot.generated.enums.Gender
import studio.styx.erisbot.generated.enums.Transactionstatus
import studio.styx.erisbot.generated.tables.records.TransactionRecord
import studio.styx.erisbot.generated.tables.records.UserRecord
import studio.styx.erisbot.generated.tables.references.TRANSACTION
import translates.TranslatesObjects.getTransferCommand
import translates.commands.economy.general.ExpectedUser
import translates.commands.economy.general.TransferTranslateInterface
import utils.ComponentBuilder
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.List
import kotlin.math.max
import kotlin.math.min


@Component
class Transfer : CommandInterface {
    @Autowired
    lateinit var dsl: DSLContext

    override suspend fun execute(event: SlashCommandInteractionEvent) {
        val user = event.user
        val userId = user.id
        val amountDouble = event.getOption("amount")!!.asDouble
        val target = if (event.getOption("user") != null) event.getOption("user")!!.asUser else null

        val t = getTransferCommand(event.getUserLocale().getLocale())

        val amount = BigDecimal.valueOf(amountDouble)

        dsl.transaction { config: Configuration ->
            val tx = config.dsl()
            // 1. Busca ambos os usuários em 1 query (otimizado)
            val userData = tx.getOrCreateUser(userId)
            val targetData = if (target != null) tx.getOrCreateUser(target.id) else null

            if (userData.money!! < amount) {
                ComponentBuilder.ContainerBuilder.create()
                    .addText(t.insufficientFunds())
                    .setEphemeral(true)
                    .reply(event)
                return@transaction
            }

            // 2. Caso: múltiplos usuários
            if (target == null) {
                handleMultipleUsers(event, userData, amount, t)
                return@transaction
            }

            // 3. Caso: transferência direta
            if (target.id == userId) {
                ComponentBuilder.ContainerBuilder.create()
                    .addText(t.tryingToSendMoneyToOurSelf())
                    .withColor(Colors.DANGER)
                    .setEphemeral(true)
                    .reply(event)
                return@transaction
            }

            if (target.id == event.jda.selfUser.id) {
                ComponentBuilder.ContainerBuilder.create()
                    .addText(t.tryingToSendMoneyToOurSelf())
                    .withColor(Colors.DANGER)
                    .setEphemeral(true)
                    .reply(event)
                return@transaction
            }

            if (target.isBot) {
                ComponentBuilder.ContainerBuilder.create()
                    .addText(t.tryingToSendMoneyToABot())
                    .withColor(Colors.DANGER)
                    .setEphemeral(true)
                    .reply(event)
                return@transaction
            }
            handleDirectTransfer(event, userData, targetData!!, amount, user, target, t)
        }
    }

    // === MÚLTIPLOS USUÁRIOS ===
    private fun handleMultipleUsers(
        event: SlashCommandInteractionEvent,
        userData: UserRecord,
        amount: BigDecimal,
        t: TransferTranslateInterface
    ) {
        val maxUsers = max(2, min(25, (userData.money!!.toDouble() / amount.toDouble()).toInt()))
        if (maxUsers < 2) {
            event.reply(t.insufficientFunds()).setEphemeral(true).queue()
            return
        }

        val menu = EntitySelectMenu.create(
            "manyTransfer/selectUsers/" + event.getUser().getId() + "/" + amount.toString(),
            EntitySelectMenu.SelectTarget.USER
        )
            .setPlaceholder(t.manyUsersUserSelectLabel())
            .setRequiredRange(1, maxUsers)
            .build()

        ComponentBuilder.ContainerBuilder.create()
            .addText(t.manyUsersTitle())
            .addDivider(false)
            .addText(t.manyUsersDescription(amount.toDouble()))
            .addRow(ActionRow.of(menu))
            .setEphemeral(false)
            .withColor(Colors.FUCHSIA)
            .reply(event)
    }

    // === TRANSFERÊNCIA DIRETA ===
    private fun handleDirectTransfer(
        event: SlashCommandInteractionEvent, userData: UserRecord, targetData: UserRecord,
        amount: BigDecimal, user: User, target: User, t: TransferTranslateInterface
    ) {
        if (userData.money!!.compareTo(amount) < 0) {
            ComponentBuilder.ContainerBuilder.create()
                .addText(t.insufficientFunds())
                .setEphemeral(true)
                .reply(event)
            return
        }

        // Cria transação
        val transaction = createTransaction(
            dsl,
            user.id,
            target.id,
            amount,
            event.channel.id,
            event.guild!!.id
        )

        // Botão de confirmação
        val confirmButton = Button.success(
            String.format("transfer/accept/%s/%s/%s/%s/%s", transaction.id, user.getId(), "0", target.getId(), "0"),
            t.buttonLabel(false)
        )

        ComponentBuilder.ContainerBuilder.create()
            .withColor(Colors.SUCCESS)
            .setEphemeral(false)
            .addText(
                t.message(
                    ExpectedUser(user.getName(), getUserGender(user.getGlobalName()!!, userData.gender), user.getId()),
                    ExpectedUser(
                        target.getName(),
                        getUserGender(target.getGlobalName()!!, targetData.gender),
                        target.getId()
                    ),
                    amount.toDouble()
                )
            )
            .addRow(ActionRow.of(confirmButton))
            .reply(event)

        LogManage.CreateLog.create()
            .setMessage(
                t.log(
                    ExpectedUser(
                        user.getGlobalName()!!,
                        getUserGender(
                            user.getGlobalName()!!,
                            userData.gender
                        ),
                        user.getId()
                    ),
                    ExpectedUser(
                        target.getGlobalName()!!,
                        getUserGender(
                            target.getGlobalName()!!,
                            targetData.gender
                        ),
                        target.getId()
                    ),
                    amount.toDouble()
                )
            )
            .setUserId(user.id)
            .setLevel(4)
            .setTags(listOf<String>("transfer", "economy", "target:" + target.getId()))
            .insert(dsl)
    }

    // === CRIA TRANSACAO ===
    private fun createTransaction(
        tx: DSLContext, userId: String?, targetId: String?, amount: BigDecimal,
        channelId: String?, guildId: String?
    ): TransactionRecord {
        val now = LocalDateTime.now()

        return tx.insertInto<TransactionRecord>(TRANSACTION)
            .set<String?>(TRANSACTION.USERID, userId)
            .set<String?>(TRANSACTION.TARGETID, targetId)
            .set<Double?>(TRANSACTION.AMOUNT, amount.toDouble())
            .set<String?>(TRANSACTION.CHANNELID, channelId)
            .set<String?>(TRANSACTION.GUILDID, guildId)
            .set<Transactionstatus?>(TRANSACTION.STATUS, Transactionstatus.PENDING)
            .set<LocalDateTime?>(TRANSACTION.CREATEDAT, now)
            .set<LocalDateTime?>(TRANSACTION.UPDATEDAT, now)
            .set<LocalDateTime?>(TRANSACTION.EXPIRESAT, now.plusHours(24))
            .returning(TRANSACTION.ID)
            .fetchOne()!!
    }

    // === GÊNERO DO USUÁRIO ===
    private fun getUserGender(name: String, gender: Gender?): GenderUnknown {
        if (gender != null) {
            return if (gender == Gender.MALE) GenderUnknown.MALE else GenderUnknown.FEMALE
        }
        return getNameGender(name)
    }

    override fun getSlashCommandData(): SlashCommandData {
        return Commands.slash("transfer", "💸 ✦ Starts a stx transfer")
            .addOptions(
                OptionData(OptionType.NUMBER, "amount", "💰 ✦ Amount of stx to transfer", true)
                    .setMinValue(20)
                    .setNameLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "quantidade")
                    .setDescriptionLocalization(
                        DiscordLocale.PORTUGUESE_BRAZILIAN,
                        "💰 ✦ Quantidade de stx para transferir"
                    )
                    .setNameLocalization(DiscordLocale.SPANISH, "cantidad")
                    .setDescriptionLocalization(DiscordLocale.SPANISH, "💰 ✦ Cantidad de stx para transferir")
                    .setNameLocalization(DiscordLocale.SPANISH_LATAM, "cantidad")
                    .setDescriptionLocalization(DiscordLocale.SPANISH_LATAM, "💰 ✦ Cantidad de stx para transferir"),
                OptionData(OptionType.USER, "user", "👤 ✦ User to transfer to (optional)", false)
                    .setNameLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "usuário")
                    .setDescriptionLocalization(
                        DiscordLocale.PORTUGUESE_BRAZILIAN,
                        "👤 ✦ Usuário para transferir (opcional)"
                    )
                    .setNameLocalization(DiscordLocale.SPANISH, "usuario")
                    .setDescriptionLocalization(DiscordLocale.SPANISH, "👤 ✦ Usuario para transferir (opcional)")
                    .setNameLocalization(DiscordLocale.SPANISH_LATAM, "usuario")
                    .setDescriptionLocalization(DiscordLocale.SPANISH_LATAM, "👤 ✦ Usuario para transferir (opcional)")
            )
            .setNameLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "transferir")
            .setDescriptionLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "💸 ✦ Inicia uma transferência de stx")
            .setNameLocalization(DiscordLocale.SPANISH, "transferir")
            .setDescriptionLocalization(DiscordLocale.SPANISH, "💸 ✦ Inicia una transferencia de stx")
            .setNameLocalization(DiscordLocale.SPANISH_LATAM, "transferir")
            .setDescriptionLocalization(DiscordLocale.SPANISH_LATAM, "💸 ✦ Inicia una transferencia de stx")
            .setNameLocalization(DiscordLocale.ENGLISH_US, "transfer")
            .setDescriptionLocalization(DiscordLocale.ENGLISH_US, "💸 ✦ Starts a stx transfer")
    }
}