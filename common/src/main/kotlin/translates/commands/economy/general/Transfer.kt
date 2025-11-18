package translates.commands.economy.general

import shared.utils.GenderUnknown
import shared.utils.Icon
import shared.utils.MentionUtil
import shared.utils.OpenEnglishGrammar
import shared.utils.OpenGrammar
import shared.utils.OpenSpanishGrammar
import shared.utils.Utils

data class ExpectedUser(
    val name: String,
    val gender: GenderUnknown = GenderUnknown.UNKNOWN,
    var id: String
)

interface TransferTranslateInterface {
    fun insufficientFunds(): String
    fun message(user: ExpectedUser, target: ExpectedUser, amount: Double): String
    fun log(user: ExpectedUser, target: ExpectedUser, amount: Double): String
    fun buttonLabel(oneAcepted: Boolean): String
    fun manyUsersTitle(): String
    fun manyUsersDescription(amount: Double): String
    fun manyUsersUserSelectLabel(): String
    fun tryingToSendMoneyToABot(): String
    fun tryingToSendMoneyToEris(): String
    fun tryingToSendMoneyToOurSelf(): String
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

        val message = Utils.replaceText("${Icon.static.get("success")} | {username} iniciou uma transação com {articleU} **{targetmention}** no valor de **{amount}**! ambos precisam apertar o botão abaixo para aceitar a transação!", mapOf(
            "articleU" to userGrammar.article,
            "articleT" to targetGrammar.article,
            "userPronoun" to userGrammar.userPronoun,
            "username" to userGrammar.name,
            "targetPronoun" to targetGrammar.userPronoun,
            "targetmention" to MentionUtil.userMention(target.id),
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

    override fun tryingToSendMoneyToABot(): String {
        return "${Icon.static.get("denied")} | Você não pode enviar stx para um bot!"
    }

    override fun tryingToSendMoneyToEris(): String {
        return "${Icon.static.get("denied")} | Eu gostaria tanto de receber esse dinheiro! Porém é contra as regras ${Icon.static.get("Eris_cry_left")}"
    }

    override fun tryingToSendMoneyToOurSelf(): String {
        return "${Icon.static.get("denied")} | Você não pode enviar stx para si mesmo!"
    }
}

private class EnglishUserGrammar(name: String, gender: GenderUnknown) : OpenEnglishGrammar(name, gender) {
    val userPronoun: String
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

        val message = Utils.replaceText("${Icon.static.get("success")} | {username} started a transaction with {articleT} **{targetmention}** for **{amount}**! Both need to press the button below to accept the transaction!", mapOf(
            "articleU" to userGrammar.article,
            "articleT" to targetGrammar.article,
            "userPronoun" to userGrammar.userPronoun,
            "username" to userGrammar.name,
            "targetPronoun" to targetGrammar.userPronoun,
            "targetmention" to MentionUtil.userMention(target.id),
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

    override fun tryingToSendMoneyToABot(): String {
        return "${Icon.static.get("denied")} | You can't send stx to a bot!"
    }

    override fun tryingToSendMoneyToEris(): String {
        return "${Icon.static.get("denied")} | I would love to receive this money! But it's against the rules ${Icon.static.get("Eris_cry_left")}"
    }

    override fun tryingToSendMoneyToOurSelf(): String {
        return "${Icon.static.get("denied")} | You can't send stx to yourself!"
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

        val message = Utils.replaceText("${Icon.static.get("success")} | ¡{username} inició una transacción con {articleT} **{targetmention}** por **{amount}**! ¡Ambos necesitan presionar el botón de abajo para aceptar la transacción!", mapOf(
            "articleU" to userGrammar.article,
            "articleT" to targetGrammar.article,
            "userPronoun" to userGrammar.userPronoun,
            "username" to userGrammar.name,
            "targetPronoun" to targetGrammar.userPronoun,
            "targetmention" to MentionUtil.userMention(target.id),
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

    override fun tryingToSendMoneyToABot(): String {
        return "${Icon.static.get("denied")} | ¡No puedes enviar stx a un bot!"
    }

    override fun tryingToSendMoneyToEris(): String {
        return "${Icon.static.get("denied")} | ¡Me encantaría recibir este dinero! Pero está contra las reglas ${Icon.static.get("Eris_cry_left")}"
    }

    override fun tryingToSendMoneyToOurSelf(): String {
        return "${Icon.static.get("denied")} | ¡No puedes enviar stx a ti mismo!"
    }
}