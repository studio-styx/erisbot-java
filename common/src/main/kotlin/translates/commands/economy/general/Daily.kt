package translates.commands.economy.general

import shared.utils.DiscordTimeStyle
import shared.utils.Icon
import shared.utils.Utils

data class PetDailyAbilities(
    val hasBonus: Boolean = false,
    val hasCooldownReduction: Boolean = false
)

enum class DailyGender {
    MASC, FEM
}

data class PetGrammar(
    val name: String,
    val gender: DailyGender,
    val abilities: PetDailyAbilities = PetDailyAbilities()
) {
    val article: String
        get() = if (gender == DailyGender.MASC) "o" else "a"

    val pronoun: String
        get() = if (gender == DailyGender.MASC) "ele" else "ela"

    val pronounStrong: String
        get() = if (gender == DailyGender.MASC) "nele" else "nela"

    fun adj(masc: String, fem: String) =
        if (gender == DailyGender.MASC) masc else fem

    val dePronoun: String
        get() = if (gender == DailyGender.MASC) "dele" else "dela"
}

object DailyPieces {

    val intro = listOf(
        "{icon} | Você recebeu **{reward}** stx no daily",
        "{icon} | Daily coletado: **{reward}** stx",
        "{icon} | Prêmio diário recebido: **{reward}** stx",
        "{icon} | Daily recebido: **{reward}** stx"
    )

    val bonus = listOf(
        "com uma ajudinha de {article} **{pet}**!",
        "{pet} encontrou algumas moedas perdidas!",
        "{pet} trouxe um bônus inesperado!",
        "{pet} resolveu colaborar hoje!",
        "{pet} cavou e achou algumas stx extras!"
    )

    val cooldownReduction = listOf(
        "{pet} ajudou a reduzir seu tempo de espera!",
        "graças à ajuda de {article} **{pet}**, seu cooldown foi reduzido!",
        "{pet} usou suas habilidades para acelerar o processo!",
        "com a magia de {article} **{pet}**, você pode coletar mais cedo!"
    )

    val bothAbilities = listOf(
        "{pet} trouxe bônus e reduziu seu cooldown!",
        "dupla ajuda de {article} **{pet}**: bônus e cooldown reduzido!",
        "{pet} foi incrível hoje - bônus extra e menos tempo de espera!",
        "graças {article} **{pet}**, você ganhou mais e esperará menos!"
    )

    val ending = listOf(
        "Agora você tem **{total}** stx.",
        "Saldo atual: **{total}** stx.",
        "Seu saldo foi atualizado para **{total}** stx.",
        "Total em carteira: **{total}** stx."
    )
}

fun assemble(parts: List<String>, ctx: Map<String, String>): String {
    val template = parts.random()
    var result = template
    ctx.forEach { (key, value) ->
        result = result.replace("{$key}", value)
    }
    return result
}

interface DailyTranslateInterface {
    fun manyAttempts(attempts: Int): String?
    fun cooldown(expiresAt: Long, petName: String?, petGender: DailyGender?, abilities: PetDailyAbilities?): String
    fun cooldown(expiresAt: Long): String
    fun message(reward: Int, total: Double): String
    fun message(reward: Int, total: Double, petName: String, petGender: DailyGender, abilities: PetDailyAbilities): String
    fun log(reward: Int): String
    fun log(reward: Int, petName: String, petGender: DailyGender, abilities: PetDailyAbilities): String
}

class DailyTranslate {
    companion object {
        @JvmStatic
        fun ptbr() = PtBrDaily()

        @JvmStatic
        fun enus() = EnUsDaily()

        @JvmStatic
        fun eses() = EsEsDaily()
    }
}

class PtBrDaily : DailyTranslateInterface {

    override fun message(reward: Int, total: Double, petName: String, petGender: DailyGender, abilities: PetDailyAbilities): String {
        val grammar = PetGrammar(
            name = petName,
            gender = petGender,
            abilities = abilities
        )

        val icon = Icon.static.get("Eris_enchanted")

        val intro = assemble(DailyPieces.intro, mapOf(
            "icon" to icon,
            "reward" to reward.toString()
        ) as Map<String, String>
        )

        val ending = assemble(DailyPieces.ending, mapOf(
            "total" to total.toString()
        ))

        // Determina qual mensagem de habilidade do pet usar
        val abilityMessage = when {
            abilities.hasBonus && abilities.hasCooldownReduction ->
                assemble(DailyPieces.bothAbilities, mapOf(
                    "pet" to grammar.name,
                    "article" to grammar.article
                ))
            abilities.hasBonus ->
                assemble(DailyPieces.bonus, mapOf(
                    "pet" to grammar.name,
                    "article" to grammar.article
                ))
            abilities.hasCooldownReduction ->
                assemble(DailyPieces.cooldownReduction, mapOf(
                    "pet" to grammar.name,
                    "article" to grammar.article
                ))
            else -> "" // Sem habilidades
        }

        return if (abilityMessage.isNotEmpty()) {
            "$intro, $abilityMessage $ending"
        } else {
            "$intro $ending"
        }
    }

    override fun message(reward: Int, total: Double): String {
        val inicialMessage = listOf(
            "${Icon.static.get("Eris_enchanted")} | Você recebeu seu daily no valor de **$reward** stx! ",
            "${Icon.static.get("Eris_happy")} | Parabéns! você resgatou seu daily diário no valor de **$reward**! ",
            "${Icon.static.get("Eris_happy")} | Você recebeu seu daily hoje no valor de **$reward**! "
        )

        val endMessage = mutableListOf(
            "Agora você tem **$total** stx.",
            "Com isso, você ficou com um total de **$total** stx",
            "E então, agora possui: **$total** stx"
        )

        if (total.toInt() == reward) {
            endMessage.addAll(listOf(
                "Agora você tem em sua carteira a mesma quantia que ganhou",
                "Como você não tinha nada, seu saldo total é igual a recompensa recebida",
                "E seu total é o mesmo que a recompensa recebida",
                "Agora você tem: **$total** stx em sua carteira, a mesma quantia que ganhou"
            ))
        }

        return inicialMessage.random() + endMessage.random()
    }

    override fun manyAttempts(attempts: Int): String? {
        return when (attempts) {
            1 -> listOf(
                "${Icon.static.get("denied")} | Eu já te disse para vir novamente mais tarde!",
                "${Icon.static.get("denied")} | Não dá pra receber duas vezes o daily no mesmo dia! eu já te disse",
                "${Icon.static.get("denied")} | Você já pegou seu daily hoje! não tente de novo"
            ).random()
            2 -> {
                val initialMessages = listOf(
                    "${Icon.static.get("denied")} | Ei! de novo? ",
                    "${Icon.static.get("Eris_Angry")} | Mas que coisa! ",
                    "${Icon.static.get("denied")} | A não, você de novo? ",
                    "${Icon.static.get("denied")} | mas você é persistente ein, "
                )

                val endMessages = listOf(
                    "Volte novamente amanhã",
                    "Volte amanhã!",
                    "Eu já te disse pra voltar mais tarde!",
                    "Eu já te disse pra voltar amanhã!",
                    "Pelo amor de deus, volta amanhã!"
                )

                return initialMessages.random() + endMessages.random()
            }
            3 -> {
                val initialMessages = listOf(
                    "${Icon.static.get("Eris_Angry")} | Mas não é possivel ",
                    "${Icon.static.get("Eris_Angry")} | Mas você é persistente ein? ",
                    "${Icon.static.get("Eris_Angry")} | Já te falei milhares de vezes pra não voltar! ",
                    "${Icon.static.get("Eris_Angry")} | Você sabe que se eu quisese você nunca mais pegava um daily, certo? "
                )

                val endMessages = listOf(
                    "${Icon.static.get("Eris_Angry")} | Volte amanhã!",
                    "${Icon.static.get("Eris_Angry")} | Só por causa disso amanhã seu daily vai ser de 1 stx!",
                    "${Icon.static.get("Eris_Angry")} | Por causa disso amanhã você não irá receber seu daily!",
                    "${Icon.static.get("Eris_Angry")} | Volte mais tarde!!"
                )

                return initialMessages.random() + endMessages.random()
            }
            4 -> listOf(
                "${Icon.static.get("Eris_Angry")} | Chega! não quero mais falar com você!",
                "${Icon.static.get("Eris_Angry")} | Não vou mais falar com você!",
                "${Icon.static.get("Eris_Angry")} | Não volte mais aqui! não irei te responder mais",
            ).random()
            else -> null
        }
    }

    override fun cooldown(expiresAt: Long, petName: String?, petGender: DailyGender?, abilities: PetDailyAbilities?): String {
        val initialMessages = listOf(
            "${Icon.static.get("denied")} | Você já pegou seu daily de hoje! ",
            "${Icon.static.get("denied")} | Não pense que vai pegar duas vezes ",
            "${Icon.static.get("denied")} | Tenho a impressão que já te vi aqui hoje "
        )

        val endMessages = listOf(
            "espero que volte ${Utils.formatDiscordTime(expiresAt, DiscordTimeStyle.RELATIVE)}",
            "tente novamente ${Utils.formatDiscordTime(expiresAt, DiscordTimeStyle.RELATIVE)}",
            "volte em ${Utils.formatDiscordTime(expiresAt, DiscordTimeStyle.RELATIVE)}",
            "te espero ${Utils.formatDiscordTime(expiresAt, DiscordTimeStyle.RELATIVE)}"
        )

        return initialMessages.random() + endMessages.random()
    }

    override fun cooldown(expiresAt: Long): String {
        return cooldown(expiresAt, null, null, null)
    }

    override fun log(reward: Int): String {
        return "Recebeu seu daily diário no valor de **$reward** stx"
    }

    override fun log(reward: Int, petName: String, petGender: DailyGender, abilities: PetDailyAbilities): String {
        val grammar = PetGrammar(
            name = petName,
            gender = petGender,
            abilities = abilities
        )

        val abilityDesc = when {
            abilities.hasBonus && abilities.hasCooldownReduction -> "com bônus e redução de cooldown"
            abilities.hasBonus -> "com bônus"
            abilities.hasCooldownReduction -> "com redução de cooldown"
            else -> ""
        }

        return "Recebeu seu daily diário no valor de **$reward** stx${if (abilityDesc.isNotEmpty()) " $abilityDesc" else ""}, graças ${grammar.article} **${grammar.name}**!"
    }

}

object DailyPiecesEn {

    val intro = listOf(
        "{icon} | You received **{reward}** stx in your daily",
        "{icon} | Daily collected: **{reward}** stx",
        "{icon} | Daily reward received: **{reward}** stx",
        "{icon} | Daily received: **{reward}** stx"
    )

    val bonus = listOf(
        "with a little help from {article} **{pet}**!",
        "{pet} found some lost coins!",
        "{pet} brought an unexpected bonus!",
        "{pet} decided to collaborate today!",
        "{pet} dug up and found some extra stx!"
    )

    val cooldownReduction = listOf(
        "{pet} helped reduce your waiting time!",
        "thanks to {article} **{pet}**'s help, your cooldown was reduced!",
        "{pet} used their skills to speed up the process!",
        "with {article} **{pet}**'s magic, you can collect earlier!"
    )

    val bothAbilities = listOf(
        "{pet} brought bonus and reduced your cooldown!",
        "double help from {article} **{pet}**: bonus and reduced cooldown!",
        "{pet} was amazing today - extra bonus and less waiting time!",
        "thanks to {article} **{pet}**, you earned more and will wait less!"
    )

    val ending = listOf(
        "You now have **{total}** stx.",
        "Current balance: **{total}** stx.",
        "Your balance has been updated to **{total}** stx.",
        "Total in wallet: **{total}** stx."
    )
}

class EnUsDaily : DailyTranslateInterface {

    override fun message(reward: Int, total: Double, petName: String, petGender: DailyGender, abilities: PetDailyAbilities): String {
        val grammar = PetGrammar(
            name = petName,
            gender = petGender,
            abilities = abilities
        )

        val icon = Icon.static.get("Eris_enchanted")

        val intro = assemble(DailyPiecesEn.intro, mapOf(
            "icon" to icon,
            "reward" to reward.toString()
        ) as Map<String, String>
        )

        val ending = assemble(DailyPiecesEn.ending, mapOf(
            "total" to total.toString()
        ))

        // Determine which pet ability message to use
        val abilityMessage = when {
            abilities.hasBonus && abilities.hasCooldownReduction ->
                assemble(DailyPiecesEn.bothAbilities, mapOf(
                    "pet" to grammar.name,
                    "article" to grammar.article
                ))
            abilities.hasBonus ->
                assemble(DailyPiecesEn.bonus, mapOf(
                    "pet" to grammar.name,
                    "article" to grammar.article
                ))
            abilities.hasCooldownReduction ->
                assemble(DailyPiecesEn.cooldownReduction, mapOf(
                    "pet" to grammar.name,
                    "article" to grammar.article
                ))
            else -> "" // No abilities
        }

        return if (abilityMessage.isNotEmpty()) {
            "$intro, $abilityMessage $ending"
        } else {
            "$intro $ending"
        }
    }

    override fun message(reward: Int, total: Double): String {
        val initialMessage = listOf(
            "${Icon.static.get("Eris_enchanted")} | You received your daily reward of **$reward** stx! ",
            "${Icon.static.get("Eris_happy")} | Congratulations! You claimed your daily reward of **$reward** stx! ",
            "${Icon.static.get("Eris_happy")} | You got your daily reward today: **$reward** stx! "
        )

        val endMessage = mutableListOf(
            "You now have **$total** stx.",
            "With that, you now have a total of **$total** stx",
            "So now you have: **$total** stx"
        )

        if (total.toInt() == reward) {
            endMessage.addAll(listOf(
                "Now you have in your wallet the exact amount you earned",
                "Since you had nothing, your total balance equals the reward received",
                "And your total is the same as the reward received",
                "You now have: **$total** stx in your wallet, the same amount you earned"
            ))
        }

        return initialMessage.random() + endMessage.random()
    }

    override fun manyAttempts(attempts: Int): String? {
        return when (attempts) {
            1 -> listOf(
                "${Icon.static.get("denied")} | I already told you to come back later!",
                "${Icon.static.get("denied")} | You can't receive the daily twice on the same day! I already told you",
                "${Icon.static.get("denied")} | You already claimed your daily today! Don't try again"
            ).random()
            2 -> {
                val initialMessages = listOf(
                    "${Icon.static.get("denied")} | Hey! Again? ",
                    "${Icon.static.get("Eris_Angry")} | Oh come on! ",
                    "${Icon.static.get("denied")} | Oh no, you again? ",
                    "${Icon.static.get("denied")} | You're quite persistent, "
                )

                val endMessages = listOf(
                    "Come back tomorrow",
                    "Return tomorrow!",
                    "I already told you to come back later!",
                    "I already told you to come back tomorrow!",
                    "For heaven's sake, come back tomorrow!"
                )

                return initialMessages.random() + endMessages.random()
            }
            3 -> {
                val initialMessages = listOf(
                    "${Icon.static.get("Eris_Angry")} | This can't be possible ",
                    "${Icon.static.get("Eris_Angry")} | You're really persistent, aren't you? ",
                    "${Icon.static.get("Eris_Angry")} | I've told you thousands of times not to come back! ",
                    "${Icon.static.get("Eris_Angry")} | You know that if I wanted, you'd never get a daily again, right? "
                )

                val endMessages = listOf(
                    "${Icon.static.get("Eris_Angry")} | Come back tomorrow!",
                    "${Icon.static.get("Eris_Angry")} | Because of this, tomorrow your daily will be only 1 stx!",
                    "${Icon.static.get("Eris_Angry")} | Because of this, you won't receive your daily tomorrow!",
                    "${Icon.static.get("Eris_Angry")} | Come back later!!"
                )

                return initialMessages.random() + endMessages.random()
            }
            4 -> listOf(
                "${Icon.static.get("Eris_Angry")} | Enough! I don't want to talk to you anymore!",
                "${Icon.static.get("Eris_Angry")} | I won't talk to you anymore!",
                "${Icon.static.get("Eris_Angry")} | Don't come back here! I won't answer you anymore",
            ).random()
            else -> null
        }
    }

    override fun cooldown(expiresAt: Long, petName: String?, petGender: DailyGender?, abilities: PetDailyAbilities?): String {
        val initialMessages = listOf(
            "${Icon.static.get("denied")} | You already claimed your daily today! ",
            "${Icon.static.get("denied")} | Don't think you can get it twice ",
            "${Icon.static.get("denied")} | I have a feeling I've seen you here today "
        )

        val endMessages = listOf(
            "hope to see you ${Utils.formatDiscordTime(expiresAt, DiscordTimeStyle.RELATIVE)}",
            "try again ${Utils.formatDiscordTime(expiresAt, DiscordTimeStyle.RELATIVE)}",
            "come back ${Utils.formatDiscordTime(expiresAt, DiscordTimeStyle.RELATIVE)}",
            "I'll be waiting ${Utils.formatDiscordTime(expiresAt, DiscordTimeStyle.RELATIVE)}"
        )

        val baseMessage = initialMessages.random() + endMessages.random()

        // Add message about cooldown reduction if pet has the ability
        return if (petName != null && abilities?.hasCooldownReduction == true) {
            val grammar = PetGrammar(petName, petGender ?: DailyGender.MASC, abilities)
            val reductionMessage = assemble(DailyPiecesEn.cooldownReduction, mapOf(
                "pet" to grammar.name,
                "article" to grammar.article
            ))
            "$baseMessage\n*$reductionMessage*"
        } else {
            baseMessage
        }
    }

    override fun cooldown(expiresAt: Long): String {
        return cooldown(expiresAt, null, null, null)
    }

    override fun log(reward: Int): String {
        return "Received their daily reward of **$reward** stx"
    }

    override fun log(reward: Int, petName: String, petGender: DailyGender, abilities: PetDailyAbilities): String {
        val grammar = PetGrammar(
            name = petName,
            gender = petGender,
            abilities = abilities
        )

        val abilityDesc = when {
            abilities.hasBonus && abilities.hasCooldownReduction -> "with bonus and cooldown reduction"
            abilities.hasBonus -> "with bonus"
            abilities.hasCooldownReduction -> "with cooldown reduction"
            else -> ""
        }

        return "Received their daily reward of **$reward** stx${if (abilityDesc.isNotEmpty()) " $abilityDesc" else ""}, thanks to ${grammar.article} **${grammar.name}**!"
    }
}

object DailyPiecesEs {

    val intro = listOf(
        "{icon} | Recibiste **{reward}** stx en tu daily",
        "{icon} | Daily recolectado: **{reward}** stx",
        "{icon} | Premio diario recibido: **{reward}** stx",
        "{icon} | Daily recibido: **{reward}** stx"
    )

    val bonus = listOf(
        "¡con una ayudita de {article} **{pet}**!",
        "¡{pet} encontró algunas monedas perdidas!",
        "¡{pet} trajo un bonus inesperado!",
        "¡{pet} decidió colaborar hoy!",
        "¡{pet} cavó y encontró algunas stx extras!"
    )

    val cooldownReduction = listOf(
        "¡{pet} ayudó a reducir tu tiempo de espera!",
        "¡gracias a la ayuda de {article} **{pet}**, tu cooldown fue reducido!",
        "¡{pet} usó sus habilidades para acelerar el proceso!",
        "¡con la magia de {article} **{pet}**, puedes recolectar más temprano!"
    )

    val bothAbilities = listOf(
        "¡{pet} trajo bonus y redujo tu cooldown!",
        "¡doble ayuda de {article} **{pet}**: bonus y cooldown reducido!",
        "¡{pet} fue increíble hoy - bonus extra y menos tiempo de espera!",
        "¡gracias a {article} **{pet}**, ganaste más y esperarás menos!"
    )

    val ending = listOf(
        "Ahora tienes **{total}** stx.",
        "Saldo actual: **{total}** stx.",
        "Tu saldo ha sido actualizado a **{total}** stx.",
        "Total en cartera: **{total}** stx."
    )
}

class EsEsDaily : DailyTranslateInterface {

    override fun message(reward: Int, total: Double, petName: String, petGender: DailyGender, abilities: PetDailyAbilities): String {
        val grammar = PetGrammar(
            name = petName,
            gender = petGender,
            abilities = abilities
        )

        val icon = Icon.static.get("Eris_enchanted")

        val intro = assemble(DailyPiecesEs.intro, mapOf(
            "icon" to icon,
            "reward" to reward.toString()
        ) as Map<String, String>
        )

        val ending = assemble(DailyPiecesEs.ending, mapOf(
            "total" to total.toString()
        ))

        // Determina qué mensaje de habilidad del pet usar
        val abilityMessage = when {
            abilities.hasBonus && abilities.hasCooldownReduction ->
                assemble(DailyPiecesEs.bothAbilities, mapOf(
                    "pet" to grammar.name,
                    "article" to grammar.article
                ))
            abilities.hasBonus ->
                assemble(DailyPiecesEs.bonus, mapOf(
                    "pet" to grammar.name,
                    "article" to grammar.article
                ))
            abilities.hasCooldownReduction ->
                assemble(DailyPiecesEs.cooldownReduction, mapOf(
                    "pet" to grammar.name,
                    "article" to grammar.article
                ))
            else -> "" // Sin habilidades
        }

        return if (abilityMessage.isNotEmpty()) {
            "$intro, $abilityMessage $ending"
        } else {
            "$intro $ending"
        }
    }

    override fun message(reward: Int, total: Double): String {
        val initialMessage = listOf(
            "${Icon.static.get("Eris_enchanted")} | ¡Recibiste tu recompensa diaria de **$reward** stx! ",
            "${Icon.static.get("Eris_happy")} | ¡Felicidades! Reclamaste tu recompensa diaria de **$reward** stx! ",
            "${Icon.static.get("Eris_happy")} | ¡Obtuviste tu recompensa diaria hoy: **$reward** stx! "
        )

        val endMessage = mutableListOf(
            "Ahora tienes **$total** stx.",
            "Con eso, ahora tienes un total de **$total** stx",
            "Así que ahora tienes: **$total** stx"
        )

        if (total.toInt() == reward) {
            endMessage.addAll(listOf(
                "Ahora tienes en tu cartera la misma cantidad que ganaste",
                "Como no tenías nada, tu saldo total es igual a la recompensa recibida",
                "Y tu total es el mismo que la recompensa recibida",
                "Ahora tienes: **$total** stx en tu cartera, la misma cantidad que ganaste"
            ))
        }

        return initialMessage.random() + endMessage.random()
    }

    override fun manyAttempts(attempts: Int): String? {
        return when (attempts) {
            1 -> listOf(
                "${Icon.static.get("denied")} | ¡Ya te dije que vuelvas más tarde!",
                "${Icon.static.get("denied")} | ¡No puedes recibir el daily dos veces en el mismo día! Ya te lo dije",
                "${Icon.static.get("denied")} | ¡Ya reclamaste tu daily hoy! No intentes de nuevo"
            ).random()
            2 -> {
                val initialMessages = listOf(
                    "${Icon.static.get("denied")} | ¡Oye! ¿Otra vez? ",
                    "${Icon.static.get("Eris_Angry")} | ¡Pero qué cosa! ",
                    "${Icon.static.get("denied")} | ¡Ay no, tú otra vez? ",
                    "${Icon.static.get("denied")} | Eres bastante persistente, "
                )

                val endMessages = listOf(
                    "Vuelve mañana",
                    "¡Regresa mañana!",
                    "¡Ya te dije que vuelvas más tarde!",
                    "¡Ya te dije que vuelvas mañana!",
                    "¡Por el amor de Dios, vuelve mañana!"
                )

                return initialMessages.random() + endMessages.random()
            }
            3 -> {
                val initialMessages = listOf(
                    "${Icon.static.get("Eris_Angry")} | Pero no es posible ",
                    "${Icon.static.get("Eris_Angry")} | ¿Pero eres persistente, eh? ",
                    "${Icon.static.get("Eris_Angry")} | ¡Ya te dije miles de veces que no volvieras! ",
                    "${Icon.static.get("Eris_Angry")} | ¿Sabes que si yo quisiera nunca más recibirías un daily, verdad? "
                )

                val endMessages = listOf(
                    "${Icon.static.get("Eris_Angry")} | ¡Vuelve mañana!",
                    "${Icon.static.get("Eris_Angry")} | ¡Por esto, mañana tu daily será de solo 1 stx!",
                    "${Icon.static.get("Eris_Angry")} | ¡Por esto, no recibirás tu daily mañana!",
                    "${Icon.static.get("Eris_Angry")} | ¡¡Vuelve más tarde!!"
                )

                return initialMessages.random() + endMessages.random()
            }
            4 -> listOf(
                "${Icon.static.get("Eris_Angry")} | ¡Basta! ¡No quiero hablar más contigo!",
                "${Icon.static.get("Eris_Angry")} | ¡No voy a hablarte más!",
                "${Icon.static.get("Eris_Angry")} | ¡No vuelvas más aquí! No te responderé más",
            ).random()
            else -> null
        }
    }

    override fun cooldown(expiresAt: Long, petName: String?, petGender: DailyGender?, abilities: PetDailyAbilities?): String {
        val initialMessages = listOf(
            "${Icon.static.get("denied")} | ¡Ya reclamaste tu daily hoy! ",
            "${Icon.static.get("denied")} | No pienses que lo puedes obtener dos veces ",
            "${Icon.static.get("denied")} | Tengo la impresión de que ya te vi aquí hoy "
        )

        val endMessages = listOf(
            "espero verte ${Utils.formatDiscordTime(expiresAt, DiscordTimeStyle.RELATIVE)}",
            "intenta nuevamente ${Utils.formatDiscordTime(expiresAt, DiscordTimeStyle.RELATIVE)}",
            "vuelve ${Utils.formatDiscordTime(expiresAt, DiscordTimeStyle.RELATIVE)}",
            "te espero ${Utils.formatDiscordTime(expiresAt, DiscordTimeStyle.RELATIVE)}"
        )

        val baseMessage = initialMessages.random() + endMessages.random()

        // Añade mensaje sobre reducción de cooldown si el pet tiene la habilidad
        return if (petName != null && abilities?.hasCooldownReduction == true) {
            val grammar = PetGrammar(petName, petGender ?: DailyGender.MASC, abilities)
            val reductionMessage = assemble(DailyPiecesEs.cooldownReduction, mapOf(
                "pet" to grammar.name,
                "article" to grammar.article
            ))
            "$baseMessage\n*$reductionMessage*"
        } else {
            baseMessage
        }
    }

    override fun cooldown(expiresAt: Long): String {
        return cooldown(expiresAt, null, null, null)
    }

    override fun log(reward: Int): String {
        return "Recibió su recompensa diaria de **$reward** stx"
    }

    override fun log(reward: Int, petName: String, petGender: DailyGender, abilities: PetDailyAbilities): String {
        val grammar = PetGrammar(
            name = petName,
            gender = petGender,
            abilities = abilities
        )

        val abilityDesc = when {
            abilities.hasBonus && abilities.hasCooldownReduction -> "con bonus y reducción de cooldown"
            abilities.hasBonus -> "con bonus"
            abilities.hasCooldownReduction -> "con reducción de cooldown"
            else -> ""
        }

        return "Recibió su recompensa diaria de **$reward** stx${if (abilityDesc.isNotEmpty()) " $abilityDesc" else ""}, ¡gracias ${grammar.article} **${grammar.name}**!"
    }
}