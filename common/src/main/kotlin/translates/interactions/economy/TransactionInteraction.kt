package translates.interactions.economy

import shared.utils.EnglishGrammar
import shared.utils.GenderUnknown
import shared.utils.Grammar
import shared.utils.Icon
import shared.utils.MentionUtil
import shared.utils.OpenEnglishGrammar
import shared.utils.OpenGrammar
import shared.utils.OpenSpanishGrammar
import shared.utils.SpanishGrammar
import shared.utils.Utils

class TransactionTransferInteraction {
    companion object {
        @JvmStatic
        fun ptbr() = PtBrTransferInteraction()

        @JvmStatic
        fun enus() = EnUsTransferInteraction()

        @JvmStatic
        fun eses() = EsEsTransferInteraction()
    }
}

data class ExpectedUserTransactionInteraction(
    val name: String,
    val gender: GenderUnknown = GenderUnknown.UNKNOWN,
    val id: String,
)

interface TransactionTransferInteractionInterface {
    fun transactionNotFound(): String
    fun transactionAlreadyAccepted(): String
    fun insufficientFunds(gender: GenderUnknown): String
    fun userNotInTransaction(): String
    fun message(
        user: ExpectedUserTransactionInteraction,
        target: ExpectedUserTransactionInteraction,
        amount: Double
    ): String
    fun transactionExpired(): String
    fun log(
        author: ExpectedUserTransactionInteraction,
        target: ExpectedUserTransactionInteraction,
        amount: Double
    ): String
    fun processing(): String
}

private class UserGrammar(gender: GenderUnknown) : OpenGrammar (
    "usuário", gender
) {
    val userPronoun: String
        get() = if (GenderUnknown.MALE == gender || GenderUnknown.UNKNOWN == gender) "pagador" else "pagadora"
}

class PtBrTransferInteraction : TransactionTransferInteractionInterface {
    override fun processing(): String {
        return "${Icon.animated.get("waiting_white")} | Processando transação..."
    }
    override fun transactionNotFound(): String {
        return "${Icon.static.get("error")} | Eu procurei por todo canto mas não consegui achar essa transação!"
    }

    override fun transactionAlreadyAccepted(): String {
        return "${Icon.static.get("denied")} | Essa transação já foi finalizada"
    }

    override fun insufficientFunds(gender: GenderUnknown): String {
        var grammar = UserGrammar(gender)

        return Utils.replaceText("{icon} | {article} {userPronoun} não tem dinheiro suficente para bancar essa transação!", mapOf(
            "icon" to Icon.static.get("Eris_cry"),
            "article" to grammar.article,
            "userPronoun" to grammar.userPronoun
        ))
    }

    override fun userNotInTransaction(): String {
        return "${Icon.static.get("denied")} | Você não faz parte dessa transação! Não venha querer roubar stx dos outros ${Icon.static.get("Eris_Angry_left")}"
    }

    override fun message(
        user: ExpectedUserTransactionInteraction,
        target: ExpectedUserTransactionInteraction,
        amount: Double
    ): String {
        var messages = listOf<String>(
            "{icon} | Pagamento realizado! {artigleU} {username} enviou **{amount}** para: {artigleT} **{targetmention}**!",
            "{icon} | Sucesso! {username} enviou **{amount}** para {artigleT} **{targetmention}**",
            "{icon} | Transação finalizada. {username} fez a boa e enviou **{amount} para **{targetmention}**"
        )

        if (amount > 1200) {
            messages.plus(listOf<String>(
                "{icon_enchanted} | Pix de milhares! {username} enviou **{amount}** para {artigleT} **{targetmention}**",
                "{icon_enchanted} | Super pagamenento de **{amount}** stx para **{targetmention}**!",
                "{icon_enchanted} | Quanto dinheiro! {artigleU} enviou **{amount}** para **{targetmention}**!"
            ))
        }

        if (amount < 50) {
            messages.plus(listOf<String>(
                "{icon} | {username} enviou uma pequena quantia de: **{amount}** stx para {artigleT} **{targetmention}**!",
                "{icon} | {artigleU} {username} deu uma esmola de **{amount}** stx para **{targetmention}**",
                "{icon} | **{targetmention}** recebeu **{amount}** stx {dePronounU} {username}!"
            ))
        }

        val authorGrammar = Grammar(
            user.name,
            user.gender
        )

        val targetGrammar = Grammar(
            target.name,
            target.gender
        )

        return Utils.replaceText(messages.random(), mapOf(
            "icon" to Icon.static.get("Eris_happy"),
            "icon_enchanted" to Icon.static.get("Eris_enchanted"),
            "username" to user.name,
            "targetname" to target.name,
            "targetmention" to MentionUtil.userMention(target.id),
            "amount" to Utils.formatNumber(amount),
            "artigleU" to authorGrammar.article,
            "artigleT" to targetGrammar.article,
            "dePronounU" to authorGrammar.dePronoun
        ))
    }

    override fun log(author: ExpectedUserTransactionInteraction, target: ExpectedUserTransactionInteraction, amount: Double): String {
        val targetGrammar = Grammar(
            target.name,
            target.gender
        )

        return Utils.replaceText("Enviou **{amount}** para {artigleT} {target}", mapOf(
            "amount" to Utils.formatNumber(amount),
            "artigleT" to targetGrammar.article,
        ))
    }

    override fun transactionExpired(): String {
        return "${Icon.static.get("denied")} | Você demorou demais para aceitar essa transação! por isso, ela foi expirada!"
    }
}

// English Version
private class EnglishUserGrammar(gender: GenderUnknown) : OpenEnglishGrammar("user", gender) {
    val userPronoun: String
        get() = when {
            gender == GenderUnknown.MALE -> "payer"
            gender == GenderUnknown.FEMALE -> "payer"
            else -> "payer"
        }
}

class EnUsTransferInteraction : TransactionTransferInteractionInterface {
    override fun processing(): String {
        return "${Icon.animated.get("waiting_white")} | Processing transaction..."
    }

    override fun transactionNotFound(): String {
        return "${Icon.static.get("error")} | I searched everywhere but couldn't find this transaction!"
    }

    override fun transactionAlreadyAccepted(): String {
        return "${Icon.static.get("denied")} | This transaction has already been completed"
    }

    override fun insufficientFunds(gender: GenderUnknown): String {
        val grammar = EnglishUserGrammar(gender)

        return Utils.replaceText("{icon} | {article} {userPronoun} doesn't have enough money to cover this transaction!", mapOf(
            "icon" to Icon.static.get("Eris_cry"),
            "article" to grammar.article,
            "userPronoun" to grammar.userPronoun
        ))
    }

    override fun userNotInTransaction(): String {
        return "${Icon.static.get("denied")} | You are not part of this transaction! Don't try to steal stx from others ${Icon.static.get("Eris_Angry_left")}"
    }

    override fun message(
        user: ExpectedUserTransactionInteraction,
        target: ExpectedUserTransactionInteraction,
        amount: Double
    ): String {
        var messages = listOf(
            "{icon} | Payment completed! {articleU} {username} sent **{amount}** to: {articleT} **{targetmention}**!",
            "{icon} | Success! {username} sent **{amount}** to {articleT} **{targetmention}**",
            "{icon} | Transaction completed. {username} did the good deed and sent **{amount}** to **{targetmention}**"
        )

        if (amount > 1200) {
            messages = messages.plus(listOf(
                "{icon_enchanted} | Big money transfer! {username} sent **{amount}** to {articleT} **{targetmention}**",
                "{icon_enchanted} | Super payment of **{amount}** stx to **{targetmention}**!",
                "{icon_enchanted} | So much money! {articleU} sent **{amount}** to **{targetmention}**!"
            ))
        }

        if (amount < 50) {
            messages = messages.plus(listOf(
                "{icon} | {username} sent a small amount of: **{amount}** stx to {articleT} **{targetmention}**!",
                "{icon} | {articleU} {username} gave a small donation of **{amount}** stx to **{targetmention}**",
                "{icon} | **{targetmention}** received **{amount}** stx from {username}!"
            ))
        }

        val authorGrammar = EnglishGrammar(
            user.name,
            user.gender
        )

        val targetGrammar = EnglishGrammar(
            target.name,
            target.gender
        )

        return Utils.replaceText(messages.random(), mapOf(
            "icon" to Icon.static.get("Eris_happy"),
            "icon_enchanted" to Icon.static.get("Eris_enchanted"),
            "username" to user.name,
            "targetname" to target.name,
            "targetmention" to MentionUtil.userMention(target.id),
            "amount" to Utils.formatNumber(amount),
            "articleU" to authorGrammar.article,
            "articleT" to targetGrammar.article
        ))
    }

    override fun log(author: ExpectedUserTransactionInteraction, target: ExpectedUserTransactionInteraction, amount: Double): String {
        val targetGrammar = EnglishGrammar(
            target.name,
            target.gender
        )

        return Utils.replaceText("Sent **{amount}** to {articleT} {target}", mapOf(
            "amount" to Utils.formatNumber(amount),
            "articleT" to targetGrammar.article,
            "target" to target.name
        ))
    }

    override fun transactionExpired(): String {
        return "${Icon.static.get("denied")} | You took too long to accept this transaction! That's why it has expired!"
    }
}

// Spanish Version
private class SpanishUserGrammar(gender: GenderUnknown) : OpenSpanishGrammar("usuario", gender) {
    val userPronoun: String
        get() = if (gender == GenderUnknown.MALE || gender == GenderUnknown.UNKNOWN) "pagador" else "pagadora"
}

class EsEsTransferInteraction : TransactionTransferInteractionInterface {
    override fun processing(): String {
        return "${Icon.animated.get("waiting_white")} | Procesando transacción..."
    }
    override fun transactionNotFound(): String {
        return "${Icon.static.get("error")} | ¡Busqué por todas partes pero no pude encontrar esta transacción!"
    }

    override fun transactionAlreadyAccepted(): String {
        return "${Icon.static.get("denied")} | Esta transacción ya ha sido finalizada"
    }

    override fun insufficientFunds(gender: GenderUnknown): String {
        val grammar = SpanishUserGrammar(gender)

        return Utils.replaceText("{icon} | ¡{article} {userPronoun} no tiene suficiente dinero para cubrir esta transacción!", mapOf(
            "icon" to Icon.static.get("Eris_cry"),
            "article" to grammar.article,
            "userPronoun" to grammar.userPronoun
        ))
    }

    override fun userNotInTransaction(): String {
        return "${Icon.static.get("denied")} | ¡No eres parte de esta transacción! No intentes robar stx de otros ${Icon.static.get("Eris_Angry_left")}"
    }

    override fun message(
        user: ExpectedUserTransactionInteraction,
        target: ExpectedUserTransactionInteraction,
        amount: Double
    ): String {
        var messages = listOf(
            "{icon} | ¡Pago realizado! {articleU} {username} envió **{amount}** a: {articleT} **{targetmention}**!",
            "{icon} | ¡Éxito! {username} envió **{amount}** a {articleT} **{targetmention}**",
            "{icon} | Transacción finalizada. {username} hizo la buena acción y envió **{amount}** a **{targetmention}**"
        )

        if (amount > 1200) {
            messages = messages.plus(listOf(
                "{icon_enchanted} | ¡Transferencia de miles! {username} envió **{amount}** a {articleT} **{targetmention}**",
                "{icon_enchanted} | ¡Super pago de **{amount}** stx a **{targetmention}**!",
                "{icon_enchanted} | ¡Cuánto dinero! {articleU} envió **{amount}** a **{targetmention}**!"
            ))
        }

        if (amount < 50) {
            messages = messages.plus(listOf(
                "{icon} | ¡{username} envió una pequeña cantidad de: **{amount}** stx a {articleT} **{targetmention}**!",
                "{icon} | {articleU} {username} dio una pequeña donación de **{amount}** stx a **{targetmention}**",
                "{icon} | ¡**{targetmention}** recibió **{amount}** stx de {username}!"
            ))
        }

        val authorGrammar = SpanishGrammar(
            user.name,
            user.gender
        )

        val targetGrammar = SpanishGrammar(
            target.name,
            target.gender
        )

        return Utils.replaceText(messages.random(), mapOf(
            "icon" to Icon.static.get("Eris_happy"),
            "icon_enchanted" to Icon.static.get("Eris_enchanted"),
            "username" to user.name,
            "targetname" to target.name,
            "targetmention" to MentionUtil.userMention(target.id),
            "amount" to Utils.formatNumber(amount),
            "articleU" to authorGrammar.article,
            "articleT" to targetGrammar.article
        ))
    }

    override fun log(author: ExpectedUserTransactionInteraction, target: ExpectedUserTransactionInteraction, amount: Double): String {
        val targetGrammar = SpanishGrammar(
            target.name,
            target.gender
        )

        return Utils.replaceText("Envió **{amount}** a {articleT} {target}", mapOf(
            "amount" to Utils.formatNumber(amount),
            "articleT" to targetGrammar.article,
            "target" to target.name
        ))
    }

    override fun transactionExpired(): String {
        return "${Icon.static.get("denied")} | ¡Te tomaste demasiado tiempo para aceptar esta transacción! Por eso, ha expirado!"
    }
}