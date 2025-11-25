package studio.styx.erisbot.discord.features.interactions.economy

import database.utils.DatabaseUtils.getOrCreateUser
import database.utils.LogManage
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
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
import studio.styx.schemaEXtended.core.schemas.BooleanSchema
import studio.styx.schemaEXtended.core.schemas.NumberSchema
import studio.styx.schemaEXtended.core.schemas.ObjectSchema
import studio.styx.schemaEXtended.core.schemas.StringSchema
import translates.TranslatesObjects.getTransactionInteraction
import translates.TranslatesObjects.getTransferCommand
import translates.commands.economy.general.ExpectedUser
import translates.commands.economy.general.TransferTranslateInterface
import translates.interactions.economy.ExpectedUserTransactionInteraction
import translates.interactions.economy.TransactionTransferInteractionInterface
import utils.ComponentBuilder
import java.math.BigDecimal
import java.util.List
import java.util.Map
import java.util.function.Consumer


@Component
class AcceptTransferInteraction : ResponderInterface {
    override fun getCustomId(): String {
        return "transfer/accept/:transactionId/:userId/:userAccepted/:targetId/:targetAccepted"
    }

    @Autowired
    lateinit var dsl: DSLContext

    override fun execute(event: ButtonInteractionEvent) {
        val customId = event.getCustomId()
        val parts: Array<String?> = customId.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

        val schema = ObjectSchema()
            .addProperty(
                "transactionId", NumberSchema()
                    .integer()
                    .coerce()
            )
            .addProperty("authorId", StringSchema())
            .addProperty("authorAccepted", BooleanSchema().coerce())
            .addProperty("targetId", StringSchema())
            .addProperty("targetAccepted", BooleanSchema().coerce())

        val result = schema.parseOrThrow(
            Map.of<String?, String?>(
                "transactionId", parts[1],
                "authorId", parts[2],
                "authorAccepted", parts[3],
                "targetId", parts[4],
                "targetAccepted", parts[5]
            )
        )

        val transactionId = result.get<Int>("transactionId")
        val authorId = result.get<String>("authorId")
        val authorAccepted = result.get<Boolean>("authorAccepted")
        val targetId = result.get<String>("targetId")
        val targetAccepted = result.get<Boolean>("targetAccepted")

        val t = getTransactionInteraction(event.getUserLocale().getLocale())

        // Verifica se o usuário clicou é um dos envolvidos
        if (event.getUser().getId() != authorId && event.getUser().getId() != targetId) {
            ComponentBuilder.ContainerBuilder.create()
                .withColor(Colors.DANGER)
                .setEphemeral(true)
                .addText(t.userNotInTransaction())
                .reply(event)
            return
        }

        val newAuthorAccepted = if (authorId == event.getUser().getId()) !authorAccepted else authorAccepted
        val newTargetAccepted = if (targetId == event.getUser().getId()) !targetAccepted else targetAccepted

        // Só processa se ambos aceitaram
        if (newAuthorAccepted && newTargetAccepted) {
            event.deferEdit().queue(Consumer { hook: InteractionHook? ->
                processCompleteTransaction(hook!!, transactionId, authorId, targetId, t)
            })
        } else {
            processPartialAcceptance(event, transactionId, authorId, targetId, newAuthorAccepted, newTargetAccepted, t)
        }
    }

    private fun processCompleteTransaction(
        hook: InteractionHook,
        transactionId: Int,
        authorId: String,
        targetId: String,
        t: TransactionTransferInteractionInterface
    ) {
        dsl.transaction { config: Configuration ->
            val tx = config.dsl()
            hook.editOriginalComponents(
                ComponentBuilder.ContainerBuilder.create()
                    .withColor(Colors.WARNING)
                    .addText(t.processing())
                    .build()
            ).useComponentsV2().queue()

            // 1. Busca transação
            val transaction = getTransaction(tx, transactionId)

            if (transaction == null) {
                sendErrorEdit(hook, t.transactionNotFound())
                return@transaction
            }

            if (transaction.status == Transactionstatus.EXPIRED) {
                sendErrorEdit(hook, t.transactionExpired())
                return@transaction
            }

            if (transaction.status != Transactionstatus.PENDING) {
                sendErrorEdit(hook, t.transactionAlreadyAccepted())
                return@transaction
            }

            // 2. Busca dados dos dois usuários de forma assíncrona
            hook.getJDA().retrieveUserById(authorId).queue(Consumer { discordAuthor: User? ->
                hook.getJDA().retrieveUserById(targetId).queue(
                    Consumer { discordTarget: User? ->
                        processUsersComplete(
                            hook,
                            tx,
                            transaction,
                            authorId,
                            targetId,
                            discordAuthor!!,
                            discordTarget!!,
                            t
                        )
                    })
            })
        }
    }

    private fun processUsersComplete(
        hook: InteractionHook, tx: DSLContext, transaction: TransactionRecord,
        authorId: String, targetId: String, discordAuthor: User, discordTarget: User,
        t: TransactionTransferInteractionInterface
    ) {
        val authorData = getOrCreateUser(tx, authorId)
        getOrCreateUser(tx, targetId)

        val amount = BigDecimal.valueOf(transaction.amount!!)

        // 3. Verifica saldo
        if (authorData.money!! < amount) {
            hook.editOriginalComponents(
                ComponentBuilder.ContainerBuilder.create()
                    .withColor(Colors.DANGER)
                    .addText(t.insufficientFunds(getUserGender(discordAuthor.getGlobalName()!!, authorData.gender)))
                    .build()
            ).useComponentsV2().queue()
            return
        }

        // 4. Executa as 3 operações em sequência (mas dentro da mesma transação = atômico)
        tx.update<TransactionRecord>(TRANSACTION)
            .set<Transactionstatus?>(TRANSACTION.STATUS, Transactionstatus.APPROVED)
            .where(TRANSACTION.ID.eq(transaction.id))
            .execute()

        // Debitar do autor e retornar novo registro
        val updatedAuthor = tx.update<UserRecord>(USER)
            .set<BigDecimal?>(USER.MONEY, USER.MONEY.minus(amount))
            .where(USER.ID.eq(authorId))
            .returning(USER.MONEY, USER.GENDER)
            .fetchOne()

        // Creditar no alvo e retornar novo registro
        val updatedTarget = tx.update<UserRecord>(USER)
            .set<BigDecimal?>(USER.MONEY, USER.MONEY.plus(amount))
            .where(USER.ID.eq(targetId))
            .returning(USER.MONEY, USER.GENDER)
            .fetchOne()


        // 5. Monta mensagem de sucesso com dados atualizados
        val expectedAuthor = ExpectedUserTransactionInteraction(
            discordAuthor.getGlobalName()!!,
            getUserGender(discordAuthor.getGlobalName()!!, updatedAuthor!!.gender),
            authorId
        )

        val expectedTarget = ExpectedUserTransactionInteraction(
            discordTarget.getGlobalName()!!,
            getUserGender(discordTarget.getGlobalName()!!, updatedTarget!!.gender),
            targetId
        )

        hook.editOriginalComponents(
            ComponentBuilder.ContainerBuilder.create()
                .withColor(Colors.SUCCESS)
                .addText(
                    t.message(
                        expectedAuthor,
                        expectedTarget,
                        amount.toDouble()
                    )
                )
                .build()
        ).useComponentsV2().queue()


        LogManage.CreateLog.create()
            .setMessage(t.log(expectedAuthor, expectedTarget, amount.toDouble()))
            .setLevel(if (amount.toDouble() > 1000) 4 else 3)
            .setUserId(authorId)
            .setTags(List.of<String>("transfer", "economy", "target:" + targetId))
            .insert(tx)
    }

    private fun processPartialAcceptance(
        event: ButtonInteractionEvent, transactionId: Int, authorId: String, targetId: String,
        newAuthorAccepted: Boolean, newTargetAccepted: Boolean,
        t: TransactionTransferInteractionInterface
    ) {
        val tc = getTransferCommand(event.getUserLocale().getLocale())

        // Busca usuários de forma assíncrona
        event.getJDA().retrieveUserById(authorId).queue(Consumer { discordAuthor: User? ->
            event.getJDA().retrieveUserById(targetId).queue(
                Consumer { discordTarget: User? ->
                    processUsersPartial(
                        event,
                        transactionId,
                        discordAuthor!!,
                        discordTarget!!,
                        newAuthorAccepted,
                        newTargetAccepted,
                        t,
                        tc
                    )
                })
        })
    }

    private fun processUsersPartial(
        event: ButtonInteractionEvent, transactionId: Int, discordAuthor: User, discordTarget: User,
        newAuthorAccepted: Boolean, newTargetAccepted: Boolean,
        t: TransactionTransferInteractionInterface, tc: Any
    ) {
        dsl.transaction { config: Configuration ->
            val tx = config!!.dsl()
            val transaction = getTransaction(tx, transactionId)
            val authorData = getOrCreateUser(tx, discordAuthor.getId())
            val targetData = getOrCreateUser(tx, discordTarget.getId())

            if (transaction == null) {
                event.editComponents(
                    ComponentBuilder.ContainerBuilder.create()
                        .withColor(Colors.DANGER)
                        .addText(t.transactionNotFound())
                        .build()
                ).queue()
                return@transaction
            }

            if (transaction.status == Transactionstatus.EXPIRED) {
                event.editComponents(
                    ComponentBuilder.ContainerBuilder.create()
                        .withColor(Colors.DANGER)
                        .addText(t.transactionExpired())
                        .build()
                ).queue()
                return@transaction
            }

            if (transaction.status != Transactionstatus.PENDING) {
                event.editComponents(
                    ComponentBuilder.ContainerBuilder.create()
                        .withColor(Colors.DANGER)
                        .addText(t.transactionAlreadyAccepted())
                        .build()
                ).queue()
                return@transaction
            }

            val confirmButton = Button.success(
                String.format(
                    "transfer/accept/%s/%s/%s/%s/%s", transaction.id, discordAuthor.getId(),
                    if (newAuthorAccepted) "1" else "0", discordTarget.getId(), if (newTargetAccepted) "1" else "0"
                ),
                (tc as TransferTranslateInterface).buttonLabel(newAuthorAccepted || newTargetAccepted)
            )

            val container = ComponentBuilder.ContainerBuilder.create()
                .withColor(Colors.SUCCESS)
                .addText(
                    tc.message(
                        ExpectedUser(
                            discordAuthor.getGlobalName()!!,
                            getUserGender(discordAuthor.getGlobalName()!!, authorData.gender),
                            discordAuthor.getId()
                        ),
                        ExpectedUser(
                            discordTarget.getName(),
                            getUserGender(discordTarget.getGlobalName()!!, targetData.gender),
                            discordTarget.getId()
                        ),
                        transaction.amount!!
                    )
                )
                .addRow(ActionRow.of(confirmButton))
                .build()
            event.editComponents(container).useComponentsV2().queue()
        }
    }

    private fun sendErrorEdit(hook: InteractionHook, message: String?) {
        hook.editOriginalComponents(
            ComponentBuilder.ContainerBuilder.create()
                .withColor(Colors.DANGER)
                .addText(message)
                .build()
        ).useComponentsV2().queue()
    }

    private fun getTransaction(tx: DSLContext, transactionId: Int): TransactionRecord? {
        return tx.selectFrom<TransactionRecord>(TRANSACTION)
            .where(TRANSACTION.ID.eq(transactionId))
            .fetchOne()
    }

    private fun getUserGender(name: String, gender: Gender?): GenderUnknown {
        if (gender != null) {
            return if (gender == Gender.MALE) GenderUnknown.MALE else GenderUnknown.FEMALE
        }
        return getNameGender(name)
    }
}