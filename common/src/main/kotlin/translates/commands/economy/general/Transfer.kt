package translates.commands.economy.general

import shared.utils.GenderUnknown
import shared.utils.Icon
import shared.utils.OpenEnglishGrammar
import shared.utils.OpenGrammar
import shared.utils.OpenSpanishGrammar
import shared.utils.Utils

data class ExpectedUser(
    val name: String,
    val gender: GenderUnknown = GenderUnknown.UNKNOWN
)

interface TransferTranslateInterface {
    fun insufficientFunds(): String
    fun message(user: ExpectedUser, target: ExpectedUser, amount: Double): String
    fun log(user: ExpectedUser, target: ExpectedUser, amount: Double): String
    fun buttonLabel(oneAcepted: Boolean): String
    fun manyUsersTitle(): String
    fun manyUsersDescription(amount: Double): String
    fun manyUsersUserSelectLabel(): String
}

class TransferTranslate {
    companion object {
        @JvmStatic
        fun ptbr() = PtBrTransfer()

        @JvmStatic
        fun enus() = EnUsTransfer()

        @JvmStatic
        fun eses() = EsEsTransfer()
    }
}

private class UserGrammar(name: String, gender: GenderUnknown) : OpenGrammar (
    name, gender
) {
    val userPronoun: String
        get() = if (GenderUnknown.MALE == gender || GenderUnknown.UNKNOWN == gender) "usuário" else "usuária"
}

class PtBrTransfer : TransferTranslateInterface {
    override fun manyUsersTitle(): String {
        return "## Multiplas transações"
    }

    override fun manyUsersUserSelectLabel(): String {
        return "Selecione os usuários"
    }

    override fun manyUsersDescription(amount: Double): String {
        return "Selecione os usuários para realizar a transação de: **$amount** stx!"
    }
    override fun insufficientFunds(): String {
        return listOf<String>(
            "${Icon.static.get("denied")} | Você não tem dinheiro suficiente para essa transação!",
            "${Icon.static.get("denied")} | Sinto muito! mas parece que você não tem o suficente para dar pra esse usuário",
            "${Icon.static.get("denied")} | Parece que você não tem dinheiro!"
        ).random()
    }

    override fun message(user: ExpectedUser, target: ExpectedUser, amount: Double): String {
        val userGrammar = UserGrammar(user.name, user.gender);
        val targetGrammar = UserGrammar(target.name, target.gender);

        val messages = listOf(
            "${Icon.static.get("success")} | {articleU} {userPronoun} {username} quer dar **{amount}** para {articleT} {targetPronoun}! ambos precisam apertar o botão abaixo para aceitar a transação!",
            "${Icon.static.get("success")} | {username} iniciou uma transação com {articleU} {targetname} no valor de **{amount}**! ambos precisam apertar o botão abaixo para aceitar a transação!",
            "${Icon.static.get("success")} | {articleU} {username} iniciou uma transferência de **{amount}** para {articleT} {targetname}! confirmem abaixo para prosseguir!",

            "${Icon.static.get("success")} | Transação iniciada! " +
                    "{articleU} {username} deseja enviar **{amount}** para {articleT} {targetPronoun}. " +
                    "ambos precisam confirmar para continuar!",

            "${Icon.static.get("success")} | {username} abriu uma operação para enviar **{amount}** " +
                    "para {articleT} {targetname}. confirmem abaixo para finalizar!",

            "${Icon.static.get("success")} | Pedido de transferência criado! " +
                    "{articleU} {username} está enviando **{amount}** {articleT} {targetPronoun}. " +
                    "falta a confirmação dos dois!",

            "${Icon.static.get("success")} | {articleU} {userPronoun} {username} propôs enviar **{amount}** " +
                    "para {articleT} {targetname}. apertem o botão para validar a transação!",

            "${Icon.static.get("success")} | Transferência pendente: {username} quer mandar **{amount}** " +
                    "para {articleT} {targetPronoun} {targetname}. os dois precisam confirmar para liberar o valor!"
        )

        val message = Utils.replaceText(messages.random(), mapOf(
            "articleU" to userGrammar.article,
            "articleT" to targetGrammar.article,
            "userPronoun" to userGrammar.userPronoun,
            "username" to userGrammar.name,
            "targetPronoun" to targetGrammar.userPronoun,
            "targetname" to targetGrammar.name,
            "amount" to amount.toString()
        ))

        return message
    }

    override fun log(user: ExpectedUser, target: ExpectedUser, amount: Double): String {
        val userGrammar = UserGrammar(user.name, user.gender);
        val targetGrammar = UserGrammar(target.name, target.gender);

        return Utils.replaceText("Iniciou uma transação com {articleT} {targetPronoun} {targetname}", mapOf(
            "articleT" to targetGrammar.article,
            "targetPronoun" to targetGrammar.userPronoun,
            "targetname" to targetGrammar.name
        ))
    }

    override fun buttonLabel(oneAcepted: Boolean): String {
        return "Aceitar ( ${if (oneAcepted) 1 else 0}/2 )"
    }
}

private class EnglishUserGrammar(name: String, gender: GenderUnknown) : OpenEnglishGrammar(name, gender) {
    override val userPronoun: String
        get() = when {
            gender == GenderUnknown.MALE -> "male user"
            gender == GenderUnknown.FEMALE -> "female user"
            else -> "user"
        }
}

class EnUsTransfer : TransferTranslateInterface {
    override fun manyUsersTitle(): String {
        return "## Multiple transactions"
    }

    override fun manyUsersUserSelectLabel(): String {
        return "Select users"
    }

    override fun manyUsersDescription(amount: Double): String {
        return "Select users to perform the transaction of: **$amount** stx!"
    }

    override fun insufficientFunds(): String {
        return listOf(
            "${Icon.static.get("denied")} | You don't have enough money for this transaction!",
            "${Icon.static.get("denied")} | Sorry! It seems you don't have enough to give to this user",
            "${Icon.static.get("denied")} | It looks like you don't have enough money!"
        ).random()
    }

    override fun message(user: ExpectedUser, target: ExpectedUser, amount: Double): String {
        val userGrammar = EnglishUserGrammar(user.name, user.gender)
        val targetGrammar = EnglishUserGrammar(target.name, target.gender)

        val formattedAmount = Utils.formatNumber(amount)

        val messages = listOf(
            "${Icon.static.get("success")} | {articleU} {userPronoun} {username} wants to give **{amount}** to {articleT} {targetPronoun}! Both need to press the button below to accept the transaction!",
            "${Icon.static.get("success")} | {username} started a transaction with {articleT} {targetname} for **{amount}**! Both need to press the button below to accept the transaction!",
            "${Icon.static.get("success")} | {articleU} {username} started a transfer of **{amount}** to {articleT} {targetname}! Confirm below to proceed!",

            "${Icon.static.get("success")} | Transaction started! " +
                    "{articleU} {username} wants to send **{amount}** to {articleT} {targetPronoun}. " +
                    "Both need to confirm to continue!",

            "${Icon.static.get("success")} | {username} opened an operation to send **{amount}** " +
                    "to {articleT} {targetname}. Confirm below to finalize!",

            "${Icon.static.get("success")} | Transfer request created! " +
                    "{articleU} {username} is sending **{amount}** to {articleT} {targetPronoun}. " +
                    "Both confirmations are needed!",

            "${Icon.static.get("success")} | {articleU} {userPronoun} {username} proposed to send **{amount}** " +
                    "to {articleT} {targetname}. Press the button to validate the transaction!",

            "${Icon.static.get("success")} | Pending transfer: {username} wants to send **{amount}** " +
                    "to {articleT} {targetPronoun}. Both need to confirm to release the amount!"
        )

        val message = Utils.replaceText(messages.random(), mapOf(
            "articleU" to userGrammar.article,
            "articleT" to targetGrammar.article,
            "userPronoun" to userGrammar.userPronoun,
            "username" to userGrammar.name,
            "targetPronoun" to targetGrammar.userPronoun,
            "targetname" to targetGrammar.name,
            "amount" to formattedAmount
        ))

        return message
    }

    override fun log(user: ExpectedUser, target: ExpectedUser, amount: Double): String {
        val targetGrammar = EnglishUserGrammar(target.name, target.gender)

        return Utils.replaceText("Started a transaction with {articleT} {targetPronoun} {targetname}", mapOf(
            "articleT" to targetGrammar.article,
            "targetPronoun" to targetGrammar.userPronoun,
            "targetname" to targetGrammar.name
        ))
    }

    override fun buttonLabel(oneAcepted: Boolean): String {
        return "Accept (${if (oneAcepted) 1 else 0}/2)"
    }
}

private class SpanishUserGrammar(name: String, gender: GenderUnknown) : OpenSpanishGrammar(name, gender) {
    val userPronoun: String
        get() = if (gender == GenderUnknown.MALE || gender == GenderUnknown.UNKNOWN) "usuario" else "usuaria"
}

class EsEsTransfer : TransferTranslateInterface {
    override fun manyUsersTitle(): String {
        return "## Múltiples transacciones"
    }

    override fun manyUsersUserSelectLabel(): String {
        return "Selecciona los usuarios"
    }

    override fun manyUsersDescription(amount: Double): String {
        return "Selecciona los usuarios para realizar la transacción de: **$amount** stx!"
    }

    override fun insufficientFunds(): String {
        return listOf(
            "${Icon.static.get("denied")} | ¡No tienes suficiente dinero para esta transacción!",
            "${Icon.static.get("denied")} | ¡Lo siento! Parece que no tienes suficiente para dar a este usuario",
            "${Icon.static.get("denied")} | ¡Parece que no tienes suficiente dinero!"
        ).random()
    }

    override fun message(user: ExpectedUser, target: ExpectedUser, amount: Double): String {
        val userGrammar = SpanishUserGrammar(user.name, user.gender)
        val targetGrammar = SpanishUserGrammar(target.name, target.gender)

        val formattedAmount = Utils.formatNumber(amount)

        val messages = listOf(
            "${Icon.static.get("success")} | ¡{articleU} {userPronoun} {username} quiere dar **{amount}** a {articleT} {targetPronoun}! ¡Ambos necesitan presionar el botón de abajo para aceptar la transacción!",
            "${Icon.static.get("success")} | ¡{username} inició una transacción con {articleT} {targetname} por **{amount}**! ¡Ambos necesitan presionar el botón de abajo para aceptar la transacción!",
            "${Icon.static.get("success")} | ¡{articleU} {username} inició una transferencia de **{amount}** a {articleT} {targetname}! ¡Confirmen abajo para proceder!",

            "${Icon.static.get("success")} | ¡Transacción iniciada! " +
                    "{articleU} {username} desea enviar **{amount}** a {articleT} {targetPronoun}. " +
                    "¡Ambos necesitan confirmar para continuar!",

            "${Icon.static.get("success")} | {username} abrió una operación para enviar **{amount}** " +
                    "a {articleT} {targetname}. ¡Confirmen abajo para finalizar!",

            "${Icon.static.get("success")} | ¡Solicitud de transferencia creada! " +
                    "{articleU} {username} está enviando **{amount}** a {articleT} {targetPronoun}. " +
                    "¡Falta la confirmación de ambos!",

            "${Icon.static.get("success")} | {articleU} {userPronoun} {username} propuso enviar **{amount}** " +
                    "a {articleT} {targetname}. ¡Presionen el botón para validar la transacción!",

            "${Icon.static.get("success")} | Transferencia pendiente: {username} quiere enviar **{amount}** " +
                    "a {articleT} {targetPronoun}. ¡Ambos necesitan confirmar para liberar el monto!"
        )

        val message = Utils.replaceText(messages.random(), mapOf(
            "articleU" to userGrammar.article,
            "articleT" to targetGrammar.article,
            "userPronoun" to userGrammar.userPronoun,
            "username" to userGrammar.name,
            "targetPronoun" to targetGrammar.userPronoun,
            "targetname" to targetGrammar.name,
            "amount" to formattedAmount
        ))

        return message
    }

    override fun log(user: ExpectedUser, target: ExpectedUser, amount: Double): String {
        val targetGrammar = SpanishUserGrammar(target.name, target.gender)

        return Utils.replaceText("Inició una transacción con {articleT} {targetPronoun} {targetname}", mapOf(
            "articleT" to targetGrammar.article,
            "targetPronoun" to targetGrammar.userPronoun,
            "targetname" to targetGrammar.name
        ))
    }

    override fun buttonLabel(oneAcepted: Boolean): String {
        return "Aceptar (${if (oneAcepted) 1 else 0}/2)"
    }
}