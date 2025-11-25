package studio.styx.erisbot.discord.features.interactions.economy

import database.utils.DatabaseUtils.getOrCreateUser
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.components.container.Container
import net.dv8tion.jda.api.components.textdisplay.TextDisplay
import net.dv8tion.jda.api.entities.IMentionable
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message.MentionType
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionHook
import org.jooq.Configuration
import org.jooq.DSLContext
import org.jooq.TransactionalRunnable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import shared.Colors
import shared.utils.GenderUnknown
import shared.utils.Utils.getNameGender
import studio.styx.erisbot.core.interfaces.ResponderInterface
import studio.styx.erisbot.generated.enums.Gender
import studio.styx.erisbot.generated.enums.Transactionstatus
import studio.styx.erisbot.generated.tables.records.TransactionRecord
import studio.styx.erisbot.generated.tables.records.UserRecord
import studio.styx.erisbot.generated.tables.references.TRANSACTION
import studio.styx.erisbot.generated.tables.references.USER
import translates.TranslatesObjects.getManyTransferInteraction
import translates.TranslatesObjects.getTransferCommand
import translates.commands.economy.general.ExpectedUser
import utils.ComponentBuilder.ContainerBuilder.Companion.create
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*
import java.util.function.Consumer
import java.util.function.Function
import java.util.stream.Collectors

@Component
class ManyTransfers : ResponderInterface {
    @Autowired
    lateinit var dsl: DSLContext

    override fun getCustomId(): String {
        return "manyTransfer/selectUsers/:userId/:amount"
    }

    override fun execute(event: EntitySelectInteractionEvent) {
        val parts = event.getCustomId().split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val authorId = parts[2]
        val amountPerUser = parts[3].toDouble()

        val t = getManyTransferInteraction(event.getUserLocale().getLocale())

        if (authorId != event.getUser().getId()) {
            event.replyComponents(
                create()
                    .addText(t.userCannotUseThisButton())
                    .withColor(Colors.DANGER)
                    .build()
            ).useComponentsV2().setEphemeral(true).queue()
            return
        }

        event.getValues().forEach(Consumer { entity: IMentionable? ->
            println("- Classe: " + entity!!.javaClass.getSimpleName() + ", ID: " + entity.getId())
        })

        val selectedUsers = event.getValues().stream()
            .filter { entity: IMentionable? -> entity is Member }  // Filtra Member
            .map<Member?> { entity: IMentionable? -> entity as Member }  // Converte para Member
            .map<User?> { obj: Member? -> obj!!.getUser() }  // Obtém o User do Member
            .filter { user: User? -> !user!!.isBot() }
            .filter { user: User? -> user!!.getId() != authorId }
            .distinct()
            .toList()

        selectedUsers.forEach(Consumer { user: User? -> println("- " + user!!.getGlobalName() + " (" + user.getId() + ")") })

        if (selectedUsers.isEmpty()) {
            event.reply("Nenhum usuário válido selecionado.").setEphemeral(true).queue()
            return
        }

        val totalAmount = amountPerUser * selectedUsers.size

        event.deferEdit().queue(Consumer { hook: InteractionHook ->
            dsl.transaction { config: Configuration ->
                val tx = config.dsl()
                val targetIds = selectedUsers.stream()
                    .map<String?> { obj: User? -> obj!!.getId() }
                    .collect(Collectors.toList())

                val targetRecords: MutableList<UserRecord?> = getOrCreateUsers(tx, targetIds)

                val authorRecord = getOrCreateUser(tx, authorId)
                if (authorRecord.money!!.toDouble() < totalAmount) {
                    println("Não tem dinheiro suficiente")
                    hook.sendMessageComponents(
                        create()
                            .withColor(Colors.DANGER)
                            .addText(t.insufficientFunds())
                            .build()
                    ).useComponentsV2().setEphemeral(true).queue()
                    return@transaction
                }

                val now = LocalDateTime.now()
                val expiresAt = now.plusHours(24)
                val channelId = event.getChannel().getId()
                val guildId = event.getGuild()!!.getId()

                val tc = getTransferCommand(event.getUserLocale().getLocale())

                hook.editOriginalComponents(
                    TextDisplay.of(
                        (if (shared.utils.Icon.animated.get("waiting_white") != null)
                            shared.utils.Icon.animated.get("waiting_white")
                        else
                            "Aguarde...")!!
                    )
                ).useComponentsV2().queue()

                val insert =
                    tx.insertInto<TransactionRecord, String?, String?, Double?, String?, String?, Transactionstatus?, LocalDateTime?, LocalDateTime?, LocalDateTime?>(
                        TRANSACTION,
                        TRANSACTION.USERID,
                        TRANSACTION.TARGETID,
                        TRANSACTION.AMOUNT,
                        TRANSACTION.CHANNELID,
                        TRANSACTION.GUILDID,
                        TRANSACTION.STATUS,
                        TRANSACTION.CREATEDAT,
                        TRANSACTION.UPDATEDAT,
                        TRANSACTION.EXPIRESAT
                    )

                for (targetId in targetIds) {
                    insert.values(
                        authorId,
                        targetId,
                        amountPerUser,
                        channelId,
                        guildId,
                        Transactionstatus.PENDING,
                        now,
                        now,
                        expiresAt
                    )
                }

                val transactions: MutableList<TransactionRecord?> = insert
                    .onDuplicateKeyIgnore()
                    .returning(TRANSACTION.ID, TRANSACTION.TARGETID)
                    .fetch()

                val transactionMap = transactions.stream()
                    .collect(
                        Collectors.toMap(
                            Function { rec: TransactionRecord? -> rec!!.targetid },
                            TransactionRecord::id
                        )
                    )

                // 6. Monta ExpectedUser para todos
                val author = ExpectedUser(
                    event.getUser().getGlobalName()!!,
                    getUserGender(event.getUser().getGlobalName()!!, authorRecord.gender),
                    authorId
                )

                val messages = selectedUsers.stream()
                    .map<Container> { user: User? ->
                        val targetId = user!!.getId()
                        val target = ExpectedUser(
                            user.getGlobalName()!!,
                            getUserGender(
                                user.getGlobalName()!!,
                                targetRecords.get(targetIds.indexOf(targetId))!!.gender
                            ),
                            targetId
                        )

                        val transactionId = transactionMap.get(targetId)
                        val customId = if (transactionId != null) String.format(
                            "transfer/accept/%d/%s/1/%s/0",
                            transactionId,
                            authorId,
                            targetId
                        ) else
                            null
                        create()
                            .withColor(Colors.SUCCESS)
                            .addText(tc.message(author, target, amountPerUser))
                            .addRow(
                                ActionRow.of(
                                    Button.success(customId!!, tc.buttonLabel(true))
                                        .withEmoji(Emoji.fromUnicode("U+1F4B0"))
                                )
                            )
                            .build()
                    }
                    .collect(Collectors.toList())

                // 7. Envia todas as mensagens
                val channel = event.getChannel()

                for (message in messages) {
                    channel.sendMessageComponents(message!!).useComponentsV2().queue()
                }
                hook.editOriginalComponents(
                    create()
                        .withColor(Colors.SUCCESS)
                        .addText("Transações iniciadas!")
                        .build()
                ).useComponentsV2().setAllowedMentions(EnumSet.noneOf<MentionType?>(MentionType::class.java)).queue()
            }
        })
    }

    private fun createTransaction(
        tx: DSLContext, userId: String?, targetId: String?, amount: BigDecimal,
        channelId: String?, guildId: String?
    ): TransactionRecord? {
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
            .fetchOne()
    }

    private fun getUserGender(name: String, gender: Gender?): GenderUnknown {
        if (gender != null) {
            return if (gender == Gender.MALE) GenderUnknown.MALE else GenderUnknown.FEMALE
        }
        return getNameGender(name)
    }

    companion object {
        private fun getOrCreateUsers(tx: DSLContext, userIds: MutableList<String?>?): MutableList<UserRecord?> {
            if (userIds == null || userIds.isEmpty()) return mutableListOf<UserRecord?>()

            // Busca usuários existentes
            val existing = tx.selectFrom<UserRecord>(USER)
                .where(USER.ID.`in`(userIds))
                .fetch()

            val existingIds = existing.stream()
                .map<String?>(UserRecord::id)
                .collect(Collectors.toSet())

            // Cria usuários faltantes
            val missingIds = userIds.stream()
                .filter { id: String? -> !existingIds.contains(id) }
                .toList()

            if (!missingIds.isEmpty()) {
                val now = LocalDateTime.now()

                // Insere usuários faltantes
                val insert = tx.insertInto<UserRecord>(USER)
                    .columns<String?, BigDecimal?, LocalDateTime?, LocalDateTime?>(
                        USER.ID,
                        USER.MONEY,
                        USER.CREATEDAT,
                        USER.UPDATEDAT
                    )

                // Adiciona valores para cada ID faltante
                for (id in missingIds) {
                    insert.values(id, BigDecimal.ZERO, now, now)
                }

                insert.onDuplicateKeyIgnore().execute()
            }

            // Retorna todos os usuários (existentes + criados) na mesma ordem dos IDs solicitados
            return tx.selectFrom<UserRecord>(USER)
                .where(USER.ID.`in`(userIds))
                .fetch()
                .into<UserRecord?>(UserRecord::class.java)
        }
    }
}