package translates.commands.economy.cassino.blackjack

import shared.utils.Icon
import shared.utils.MentionUtil
import shared.utils.Utils

interface MultiplayerBlackjackTranslateInterface {
    fun multiplayerTitle(userId: String, targetId: String, amount: Double): String
    fun multiplayerUserHand(userId: String, userCards: List<Card>, handValue: Int, endGame: Boolean = false): String
    fun multiplayerTurn(endGame: String?, turn: String, userId: String, targetId: String): String
    val multiplayerButtons: BlackjackButtons
}

class BlackjackMultiplayerTranslate {
    companion object {
        @JvmStatic
        fun ptbr() = PtBrMultiplayerBlackjack()

        @JvmStatic
        fun enus() = EnUsMultiplayerBlackjack()

        @JvmStatic
        fun eses() = EsEsMultiplayerBlackjack()
    }
}


class PtBrMultiplayerBlackjack : MultiplayerBlackjackTranslateInterface {
    override fun multiplayerTitle(userId: String, targetId: String, amount: Double): String {
        return Utils.brBuilder(
            "## Partida de Blackjack Multiplayer",
            "**Jogador 1:** ${MentionUtil.userMention(userId)}",
            "**Jogador 2:** ${MentionUtil.userMention(targetId)}",
            "**Aposta:** $amount stx"
        )
    }

    override fun multiplayerUserHand(userId: String, userCards: List<Card>, handValue: Int, endGame: Boolean): String {
        val cardsText = if (endGame) {
            userCards.map { "**`${it.name}`**" }.joinToString(", ")
        } else {
            userCards.mapIndexed { index, card ->
                if (index == 0) "**`${card.name}`**" else "**`?`**"
            }.joinToString(", ")
        }

        val handValueText = if (endGame) "**Valor da mão:** **$handValue**" else null

        return Utils.brBuilder(
            "### ( ${Icon.static.get("card_joker")} ╺╸ Cartas de ${MentionUtil.userMention(userId)}",
            cardsText,
            handValueText
        )
    }

    override fun multiplayerTurn(endGame: String?, turn: String, userId: String, targetId: String): String {
        return if (endGame != null) {
            endGame
        } else {
            val player = if (turn == "player") MentionUtil.userMention(userId) else MentionUtil.userMention(targetId)
            "**Turno de:** $player"
        }
    }

    override val multiplayerButtons = BlackjackButtons(
        hit = "Pegar uma carta",
        pass = "Passar",
        stand = "Parar"
    )
}

class EnUsMultiplayerBlackjack : MultiplayerBlackjackTranslateInterface {
    override fun multiplayerTitle(userId: String, targetId: String, amount: Double): String {
        return Utils.brBuilder(
            "## Multiplayer Blackjack Match",
            "**Player 1:** ${MentionUtil.userMention(userId)}",
            "**Player 2:** ${MentionUtil.userMention(targetId)}",
            "**Bet:** $amount stx"
        )
    }

    override fun multiplayerUserHand(userId: String, userCards: List<Card>, handValue: Int, endGame: Boolean): String {
        val cardsText = if (endGame) {
            userCards.map { "**`${it.name}`**" }.joinToString(", ")
        } else {
            userCards.mapIndexed { index, card ->
                if (index == 0) "**`${card.name}`**" else "**`?`**"
            }.joinToString(", ")
        }

        val handValueText = if (endGame) "**Hand value:** **$handValue**" else null

        return Utils.brBuilder(
            "### ( ${Icon.static.get("card_joker")} ╺╸ ${MentionUtil.userMention(userId)}'s cards",
            cardsText,
            handValueText
        )
    }

    override fun multiplayerTurn(endGame: String?, turn: String, userId: String, targetId: String): String {
        return if (endGame != null) {
            endGame
        } else {
            val player = if (turn == "player") MentionUtil.userMention(userId) else MentionUtil.userMention(targetId)
            "**Turn of:** $player"
        }
    }

    override val multiplayerButtons = BlackjackButtons(
        hit = "Take a card",
        pass = "Pass",
        stand = "Stand"
    )
}

class EsEsMultiplayerBlackjack : MultiplayerBlackjackTranslateInterface {
    override fun multiplayerTitle(userId: String, targetId: String, amount: Double): String {
        return Utils.brBuilder(
            "## Partida de Blackjack Multijugador",
            "**Jugador 1:** ${MentionUtil.userMention(userId)}",
            "**Jugador 2:** ${MentionUtil.userMention(targetId)}",
            "**Apuesta:** $amount stx"
        )
    }

    override fun multiplayerUserHand(userId: String, userCards: List<Card>, handValue: Int, endGame: Boolean): String {
        val cardsText = if (endGame) {
            userCards.map { "**`${it.name}`**" }.joinToString(", ")
        } else {
            userCards.mapIndexed { index, card ->
                if (index == 0) "**`${card.name}`**" else "**`?`**"
            }.joinToString(", ")
        }

        val handValueText = if (endGame) "**Valor de mano:** **$handValue**" else null

        return Utils.brBuilder(
            "### ( ${Icon.static.get("card_joker")} ╺╸ Cartas de ${MentionUtil.userMention(userId)}",
            cardsText,
            handValueText
        )
    }

    override fun multiplayerTurn(endGame: String?, turn: String, userId: String, targetId: String): String {
        return if (endGame != null) {
            endGame
        } else {
            val player = if (turn == "player") MentionUtil.userMention(userId) else MentionUtil.userMention(targetId)
            "**Turno de:** $player"
        }
    }

    override val multiplayerButtons = BlackjackButtons(
        hit = "Tomar una carta",
        pass = "Pasar",
        stand = "Plantarse"
    )
}