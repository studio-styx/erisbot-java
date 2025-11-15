package translates.commands.economy.general

import shared.utils.Icon
import shared.utils.MentionUtil
import shared.utils.Utils

interface BalanceTranslateInterface {
    val erisMoney: String
    val botMoney: String
    fun message(money: Double, id: String): List<String>
    fun log(id: String, userId: String): String
}

class BalanceTranslate {
    companion object {
        @JvmStatic
        fun ptbr() = PtBrBalance()

        @JvmStatic
        fun enus() = EnUsBalance()

        @JvmStatic
        fun eses() = EsEsBalance()
    }
}

private fun gerarFrase(
    introducoes: List<String>,
    comentarios: List<String>,
    ctx: MoneyContext
): String {
    val intro = introducoes.random()
    val comentario = comentarios.random()

    return buildString {
        append(ctx.icon)
        append(" | ")
        append(
            intro
                .replace("{user}", ctx.user)
                .replace("{amount}", "**${ctx.amount}**")
        )
        append(", ")
        append(comentario)
    }
}

class PtBrBalance : BalanceTranslateInterface {
    override val erisMoney = "**${Icon.static.get("Eris_cry")} | Eu sou pobre, eu não tenho dinheiro! ${Icon.static.get("Eris_shy_left")}**"
    override val botMoney = "${Icon.static.get("denied")} | Você não pode ver o saldo de um bot!"

    override fun message(money: Double, id: String): List<String> {
        val user = MentionUtil.userMention(id)
        val amount = Utils.formatNumber(money)

        val icon = when {
            money > 800 -> Icon.static.get("Eris_enchanted")
            money > 200 -> Icon.static.get("money_bag")
            else -> Icon.static.get("money")
        }

        val ctx = MoneyContext(
            user = user,
            amount = amount,
            icon = icon ?: "null"
        )

        val result = mutableListOf<String>()

        when {
            money > 800 -> {
                repeat(5) {
                    result.add(
                        gerarFrase(introducoesRicas, comentariosRicos, ctx)
                    )
                }
            }
            money > 200 -> {
                repeat(5) {
                    result.add(
                        gerarFrase(introducoesMedias, comentariosMedios, ctx)
                    )
                }
            }
            else -> {
                repeat(5) {
                    result.add(
                        gerarFrase(introducoesBaixas, comentariosBaixos, ctx)
                    )
                }
            }
        }

        return result
    }


    override fun log(id: String, userId: String): String {
        return if (id == userId) {
            "Verificou o próprio saldo"
        } else {
            "Verificou o saldo de ${MentionUtil.userMention(id)}"
        }
    }
}

class EnUsBalance : BalanceTranslateInterface {
    override val erisMoney = "**${Icon.static.get("Eris_cry")} | I'm broke... I have no money! ${Icon.static.get("Eris_shy_left")}**"
    override val botMoney = "${Icon.static.get("denied")} | You can't check a bot's balance!"

    override fun message(money: Double, id: String): List<String> {
        val user = MentionUtil.userMention(id)
        val amount = Utils.formatNumber(money)

        val icon = when {
            money > 800 -> Icon.static.get("Eris_enchanted")
            money > 200 -> Icon.static.get("money_bag")
            else -> Icon.static.get("money")
        }

        val ctx = MoneyContext(
            user = user,
            amount = amount,
            icon = icon ?: "null"
        )

        val result = mutableListOf<String>()

        when {
            money > 800 -> {
                repeat(5) {
                    result.add(
                        gerarFrase(introducoesRicasEn, comentariosRicosEn, ctx)
                    )
                }
            }
            money > 200 -> {
                repeat(5) {
                    result.add(
                        gerarFrase(introducoesMediasEn, comentariosMediosEn, ctx)
                    )
                }
            }
            else -> {
                repeat(5) {
                    result.add(
                        gerarFrase(introducoesBaixasEn, comentariosBaixosEn, ctx)
                    )
                }
            }
        }

        if (money > 500) {
            repeat(2) {
                result.add(
                    gerarFrase(introducoesCoragemEn, comentariosCoragemEn, ctx)
                )
            }
        }

        return result
    }

    override fun log(id: String, userId: String): String {
        return if (id == userId) {
            "Checked their own balance"
        } else {
            "Checked ${MentionUtil.userMention(id)}'s balance"
        }
    }
}

class EsEsBalance : BalanceTranslateInterface {
    override val erisMoney = "**${Icon.static.get("Eris_cry")} | ¡Estoy pobre, no tengo dinero! ${Icon.static.get("Eris_shy_left")}**"
    override val botMoney = "${Icon.static.get("denied")} | ¡No puedes ver el saldo de un bot!"

    override fun message(money: Double, id: String): List<String> {
        val user = MentionUtil.userMention(id)
        val amount = Utils.formatNumber(money)

        val icon = when {
            money > 800 -> Icon.static.get("Eris_enchanted")
            money > 200 -> Icon.static.get("money_bag")
            else -> Icon.static.get("money")
        }

        val ctx = MoneyContext(
            user = user,
            amount = amount,
            icon = icon ?: "null"
        )

        val result = mutableListOf<String>()

        when {
            money > 800 -> {
                repeat(5) {
                    result.add(
                        gerarFrase(introducoesRicasEs, comentariosRicosEs, ctx)
                    )
                }
            }
            money > 200 -> {
                repeat(5) {
                    result.add(
                        gerarFrase(introducoesMediasEs, comentariosMediosEs, ctx)
                    )
                }
            }
            else -> {
                repeat(5) {
                    result.add(
                        gerarFrase(introducoesBaixasEs, comentariosBaixosEs, ctx)
                    )
                }
            }
        }

        if (money > 500) {
            repeat(2) {
                result.add(
                    gerarFrase(introducoesCoragemEs, comentariosCoragemEs, ctx)
                )
            }
        }

        return result
    }

    override fun log(id: String, userId: String): String {
        return if (id == userId) {
            "Revisó su propio saldo"
        } else {
            "Revisó el saldo de ${MentionUtil.userMention(id)}"
        }
    }
}

// Listas para Inglês
private val introducoesRicasEn = listOf(
    "{user} is carrying incredible {amount} stx",
    "{user} has an impressive balance of {amount} stx",
    "{user} has accumulated {amount} stx — enviable!",
    "{user} has no less than {amount} stx"
)

private val comentariosRicosEn = listOf(
    "I wish I could be like that someday",
    "they could share some with me",
    "so jealous!",
    "that's too much money to carry around",
    "they must be proud of all that"
)

private val introducoesMediasEn = listOf(
    "{user} has {amount} stx",
    "{user} is with {amount} stx in balance",
    "{user} carrying {amount} stx",
    "{user} possesses {amount} stx"
)

private val comentariosMediosEn = listOf(
    "could be more",
    "it's already something",
    "it's not that little",
    "they must be happy with that"
)

private val introducoesBaixasEn = listOf(
    "{user} has only {amount} stx",
    "{user} is with just {amount} stx",
    "{user} possesses only {amount} stx",
    "{user} has {amount} stx"
)

private val comentariosBaixosEn = listOf(
    "someone help them!",
    "how does anyone survive like that?",
    "it's very little...",
    "can't do much with that"
)

private val introducoesCoragemEn = listOf(
    "{user} has {amount} stx",
    "{user} is carrying {amount} stx"
)

private val comentariosCoragemEn = listOf(
    "brave enough to walk around with that much cash?",
    "carrying that much is risky!"
)

// Listas para Espanhol
private val introducoesRicasEs = listOf(
    "{user} está cargando increíbles {amount} stx",
    "{user} tiene un saldo impresionante de {amount} stx",
    "{user} ha acumulado {amount} stx — ¡envidiable!",
    "{user} posee nada menos que {amount} stx"
)

private val comentariosRicosEs = listOf(
    "ojalá pudiera ser así algún día",
    "podría compartir un poco conmigo",
    "¡qué envidia!",
    "eso es demasiado dinero para andar en el bolsillo",
    "debe estar orgulloso de todo eso"
)

private val introducoesMediasEs = listOf(
    "{user} tiene {amount} stx",
    "{user} está con {amount} stx en el saldo",
    "{user} cargando {amount} stx",
    "{user} posee {amount} stx"
)

private val comentariosMediosEs = listOf(
    "podría ser más",
    "ya es algo",
    "no es tan poco",
    "seguramente está contento con eso"
)

private val introducoesBaixasEs = listOf(
    "{user} tiene solo {amount} stx",
    "{user} está con solo {amount} stx",
    "{user} posee solamente {amount} stx",
    "{user} tiene {amount} stx"
)

private val comentariosBaixosEs = listOf(
    "¡alguien debería ayudarlo!",
    "¿cómo sobrevive alguien así?",
    "es muy poco...",
    "no se puede hacer mucho con eso"
)

private val introducoesCoragemEs = listOf(
    "{user} tiene {amount} stx",
    "{user} está cargando {amount} stx"
)

private val comentariosCoragemEs = listOf(
    "¿cómo se atreve a andar con tanto dinero encima?",
    "¡qué valiente por llevar tanto!"
)

private data class MoneyContext(
    val user: String,
    val amount: String,
    val icon: String
)

private val introducoesRicas = listOf(
    "{user} está carregando incríveis {amount} stx",
    "{user} tem um saldo impressionante de {amount} stx",
    "{user} acumulou {amount} stx — invejável!",
    "{user} possui nada menos que {amount} stx"
)

private val comentariosRicos = listOf(
    "eu queria ser assim um dia",
    "ele poderia dividir comigo",
    "que inveja!",
    "isso é dinheiro demais pra andar no bolso",
    "ele deve estar orgulhoso disso tudo"
)

private val introducoesMedias = listOf(
    "{user} tem {amount} stx",
    "{user} está com {amount} stx no saldo",
    "{user} carregando {amount} stx",
    "{user} possui {amount} stx"
)

private val comentariosMedios = listOf(
    "poderia ser mais",
    "já é alguma coisa",
    "não é tão pouco assim",
    "ele deve estar feliz com isso"
)

private val introducoesBaixas = listOf(
    "{user} tem apenas {amount} stx",
    "{user} está com só {amount} stx",
    "{user} possui somente {amount} stx",
    "{user} tem {amount} stx"
)

private val comentariosBaixos = listOf(
    "alguém ajuda ele!",
    "como alguém sobrevive assim?",
    "é muito pouco...",
    "não dá pra fazer muita coisa com isso"
)