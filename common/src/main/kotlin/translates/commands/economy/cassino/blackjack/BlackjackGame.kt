package translates.commands.economy.cassino.blackjack

import shared.utils.Icon
import shared.utils.Utils

interface BlackjackTranslateInterface {
    fun title(difficulty: Int): String
    fun erisHand(erisCards: List<Card>, handValue: Int, difficulty: Int, hide: Boolean = true): String
    fun userHand(userCards: List<Card>, handValue: Int): String
    fun humor(humor: String): String
    fun winsMessage(wins: String, amount: Double, multiplier: Double): String
    fun erisAction(action: String?): String
    val buttons: BlackjackButtons
}

data class BlackjackButtons(
    val hit: String,
    val pass: String,
    val stand: String
)

data class Card(
    val name: String,
    val value: Int
)

class BlackjackTranslate {
    companion object {
        @JvmStatic
        fun ptbr() = PtBrBlackjack()

        @JvmStatic
        fun enus() = EnUsBlackjack()

        @JvmStatic
        fun eses() = EsEsBlackjack()
    }
}

class PtBrBlackjack : BlackjackTranslateInterface {
    override fun title(difficulty: Int): String {
        val difficultyText = when (difficulty) {
            0 -> "Dealer comum"
            1 -> "Fácil"
            2 -> "Normal"
            3 -> "Difícil"
            4 -> "Pesadelo"
            else -> "?"
        }
        return Utils.brBuilder(
            "## 🃏 BlackJack",
            "-# ╰ Dificuldade selecionada: $difficultyText"
        )
    }

    override fun erisHand(erisCards: List<Card>, handValue: Int, difficulty: Int, hide: Boolean): String {
        val dealerName = if (difficulty == 0) "do Dealer" else "da Éris"
        val cardsText = if (hide) {
            erisCards.mapIndexed { index, card ->
                if (index == 0) "**`${card.name}`**" else "**`?`**"
            }.joinToString(", ")
        } else {
            erisCards.map { "**`${it.name}`**" }.joinToString(", ")
        }

        val handValueText = if (!hide) "Mão: $handValue" else null

        return Utils.brBuilder(
            "### ( ${Icon.static.get("card_joker")} ╺╸ Cartas $dealerName",
            cardsText,
            handValueText
        )
    }

    override fun userHand(userCards: List<Card>, handValue: Int): String {
        val cardsText = userCards.map { "**`${it.name}`**" }.joinToString(", ")
        return Utils.brBuilder(
            "### ( ${Icon.static.get("card_joker")} ╺╸ Suas cartas",
            cardsText,
            "Mão: $handValue"
        )
    }

    override fun humor(humor: String): String {
        val (icon, mood) = when (humor) {
            "happy" -> Pair("Eris_happy", "Feliz")
            "angry" -> Pair("Eris_Angry", "Furiosa")
            "sad" -> Pair("Eris_cry", "Triste")
            "neutral" -> Pair("Eris_thinking", "Neutra")
            "scared" -> Pair("Eris_shy", "Calma")
            "surprised" -> Pair("Eris_enchanted", "Surpresa")
            "confused" -> Pair("Eris_thinking", "Confusa")
            else -> Pair("?", "?")
        }

        return Utils.brBuilder(
            "### ( ${Icon.static.get(icon)} ╺╸ Humor da Éris: $mood"
        )
    }

    override fun winsMessage(wins: String, amount: Double, multiplier: Double): String {
        return when (wins) {
            "eris" -> "## Você apostou: $amount stx e perdeu!"
            "user" -> {
                val winAmount = amount * multiplier
                val formattedAmount = if (winAmount % 1 == 0.0)
                    winAmount.toInt().toString()
                else
                    "%.2f".format(winAmount)
                "## Você apostou: $amount stx e ganhou: $formattedAmount stx!"
            }
            "draw" -> "## Você apostou: $amount stx e a partida acabou em empate!"
            else -> "## Resultado desconhecido"
        }
    }

    override fun erisAction(action: String?): String {
        val actionText = when (action) {
            "hit" -> "Pegou uma carta"
            "pass" -> "Passou"
            "stand" -> "Parou"
            "thinking" -> "Pensando"
            else -> "?"
        }
        return "Ação da éris: $actionText"
    }

    override val buttons = BlackjackButtons(
        hit = "Pegar uma carta",
        pass = "Passar",
        stand = "Parar"
    )
}

class EnUsBlackjack : BlackjackTranslateInterface {
    override fun title(difficulty: Int): String {
        val difficultyText = when (difficulty) {
            0 -> "Common Dealer"
            1 -> "Easy"
            2 -> "Normal"
            3 -> "Hard"
            4 -> "Nightmare"
            else -> "?"
        }
        return Utils.brBuilder(
            "## 🃏 BlackJack",
            "-# ╰ Selected difficulty: $difficultyText"
        )
    }

    override fun erisHand(erisCards: List<Card>, handValue: Int, difficulty: Int, hide: Boolean): String {
        val dealerName = if (difficulty == 0) "Dealer's" else "Eris'"
        val cardsText = if (hide) {
            erisCards.mapIndexed { index, card ->
                if (index == 0) "**`${card.name}`**" else "**`?`**"
            }.joinToString(", ")
        } else {
            erisCards.map { "**`${it.name}`**" }.joinToString(", ")
        }

        val handValueText = if (!hide) "Hand: $handValue" else null

        return Utils.brBuilder(
            "### ( ${Icon.static.get("card_joker")} ╺╸ $dealerName cards",
            cardsText,
            handValueText
        )
    }

    override fun userHand(userCards: List<Card>, handValue: Int): String {
        val cardsText = userCards.map { "**`${it.name}`**" }.joinToString(", ")
        return Utils.brBuilder(
            "### ( ${Icon.static.get("card_joker")} ╺╸ Your cards",
            cardsText,
            "Hand: $handValue"
        )
    }

    override fun humor(humor: String): String {
        val (icon, mood) = when (humor) {
            "happy" -> Pair("Eris_happy", "Happy")
            "angry" -> Pair("Eris_Angry", "Angry")
            "sad" -> Pair("Eris_cry", "Sad")
            "neutral" -> Pair("Eris_thinking", "Neutral")
            "scared" -> Pair("Eris_shy", "Calm")
            "surprised" -> Pair("Eris_enchanted", "Surprised")
            "confused" -> Pair("Eris_thinking", "Confused")
            else -> Pair("?", "?")
        }

        return Utils.brBuilder(
            "### ( ${Icon.static.get(icon)} ╺╸ Eris' mood: $mood"
        )
    }

    override fun winsMessage(wins: String, amount: Double, multiplier: Double): String {
        return when (wins) {
            "eris" -> "## You bet: $amount stx and lost!"
            "user" -> {
                val winAmount = amount * multiplier
                val formattedAmount = if (winAmount % 1 == 0.0)
                    winAmount.toInt().toString()
                else
                    "%.2f".format(winAmount)
                "## You bet: $amount stx and won: $formattedAmount stx!"
            }
            "draw" -> "## You bet: $amount stx and the match ended in a draw!"
            else -> "## Unknown result"
        }
    }

    override fun erisAction(action: String?): String {
        val actionText = when (action) {
            "hit" -> "Took a card"
            "pass" -> "Passed"
            "stand" -> "Stood"
            "thinking" -> "Thinking"
            else -> "?"
        }
        return "Eris' action: $actionText"
    }

    override val buttons = BlackjackButtons(
        hit = "Take a card",
        pass = "Pass",
        stand = "Stand"
    )
}

class EsEsBlackjack : BlackjackTranslateInterface {
    override fun title(difficulty: Int): String {
        val difficultyText = when (difficulty) {
            0 -> "Repartidor común"
            1 -> "Fácil"
            2 -> "Normal"
            3 -> "Difícil"
            4 -> "Pesadilla"
            else -> "?"
        }
        return Utils.brBuilder(
            "## 🃏 BlackJack",
            "-# ╰ Dificultad seleccionada: $difficultyText"
        )
    }

    override fun erisHand(erisCards: List<Card>, handValue: Int, difficulty: Int, hide: Boolean): String {
        val dealerName = if (difficulty == 0) "del Repartidor" else "de Éris"
        val cardsText = if (hide) {
            erisCards.mapIndexed { index, card ->
                if (index == 0) "**`${card.name}`**" else "**`?`**"
            }.joinToString(", ")
        } else {
            erisCards.map { "**`${it.name}`**" }.joinToString(", ")
        }

        val handValueText = if (!hide) "Mano: $handValue" else null

        return Utils.brBuilder(
            "### ( ${Icon.static.get("card_joker")} ╺╸ Cartas $dealerName",
            cardsText,
            handValueText
        )
    }

    override fun userHand(userCards: List<Card>, handValue: Int): String {
        val cardsText = userCards.map { "**`${it.name}`**" }.joinToString(", ")
        return Utils.brBuilder(
            "### ( ${Icon.static.get("card_joker")} ╺╸ Tus cartas",
            cardsText,
            "Mano: $handValue"
        )
    }

    override fun humor(humor: String): String {
        val (icon, mood) = when (humor) {
            "happy" -> Pair("Eris_happy", "Feliz")
            "angry" -> Pair("Eris_Angry", "Furiosa")
            "sad" -> Pair("Eris_cry", "Triste")
            "neutral" -> Pair("Eris_thinking", "Neutral")
            "scared" -> Pair("Eris_shy", "Calma")
            "surprised" -> Pair("Eris_enchanted", "Sorprendida")
            "confused" -> Pair("Eris_thinking", "Confusa")
            else -> Pair("?", "?")
        }

        return Utils.brBuilder(
            "### ( ${Icon.static.get(icon)} ╺╸ Humor de Éris: $mood"
        )
    }

    override fun winsMessage(wins: String, amount: Double, multiplier: Double): String {
        return when (wins) {
            "eris" -> "## Apostaste: $amount stx y perdiste!"
            "user" -> {
                val winAmount = amount * multiplier
                val formattedAmount = if (winAmount % 1 == 0.0)
                    winAmount.toInt().toString()
                else
                    "%.2f".format(winAmount)
                "## Apostaste: $amount stx y ganaste: $formattedAmount stx!"
            }
            "draw" -> "## Apostaste: $amount stx y el partido terminó en empate!"
            else -> "## Resultado desconocido"
        }
    }

    override fun erisAction(action: String?): String {
        val actionText = when (action) {
            "hit" -> "Tomó una carta"
            "pass" -> "Pasó"
            "stand" -> "Se plantó"
            "thinking" -> "Pensando"
            else -> "?"
        }
        return "Acción de Éris: $actionText"
    }

    override val buttons = BlackjackButtons(
        hit = "Tomar una carta",
        pass = "Pasar",
        stand = "Plantarse"
    )
}