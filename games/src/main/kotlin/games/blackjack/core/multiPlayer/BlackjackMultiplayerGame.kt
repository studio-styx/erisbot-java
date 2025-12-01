package games.blackjack.core.multiPlayer

import games.blackjack.core.dtos.BlackjackCardObject
import games.blackjack.core.singlePlayer.BlackjackEndGameResultType
import translates.commands.economy.cassino.blackjack.BlackjackMultiplayerTranslate
import translates.commands.economy.cassino.blackjack.MultiplayerBlackjackTranslateInterface
import kotlin.random.Random

class BlackjackMultiplayerGame(
    val userId: String,
    val targetId: String,
    val amountAposted: Double
) {
    var userCards: MutableList<BlackjackCardObject> = mutableListOf()
        private set
    var targetCards: MutableList<BlackjackCardObject> = mutableListOf()
        private set
    var remainingCards: MutableList<BlackjackCardObject> = mutableListOf()
        private set
    var turnCount: Int = 0
    var passCount: Int = 0
    var turn: BlackjackMultiplayerPlayers = BlackjackMultiplayerPlayers.PLAYER

    var t: MultiplayerBlackjackTranslateInterface = BlackjackMultiplayerTranslate.ptbr()


    private fun setDefaultDeck() {
        val fullDeck = mutableListOf<BlackjackCardObject>()

        val cards = listOf(
            BlackjackCardObject(11, "A"),
            BlackjackCardObject(2, "2"),
            BlackjackCardObject(3, "3"),
            BlackjackCardObject(4, "4"),
            BlackjackCardObject(5, "5"),
            BlackjackCardObject(6, "6"),
            BlackjackCardObject(7, "7"),
            BlackjackCardObject(8, "8"),
            BlackjackCardObject(9, "9"),
            BlackjackCardObject(10, "10"),
            BlackjackCardObject(10, "J"),
            BlackjackCardObject(10, "Q"),
            BlackjackCardObject(10, "K")
        )

        for (i in 0 until 4) {
            for (card in cards) {
                fullDeck.add(card)
            }
        }

        remainingCards = fullDeck
    }

    fun calculateHandValue(hand: List<BlackjackCardObject>): Int {
        var total = 0
        var ases = 0

        for (card in hand) {
            total += card.number
            if (card.card === "A") ases++
        }

        // Se estourar, transformar Ases de 11 em 1
        while (total > 21 && ases > 0) {
            total -= 10 // Subtrai 11 e soma 1, ou seja, subtrai 10
            ases--
        }

        return total
    }

    private fun shuffleDeck() {
        remainingCards.shuffle()
    }

    private fun drawCard(): BlackjackCardObject {
        if (remainingCards.isEmpty()) {
            setDefaultDeck()
            shuffleDeck()
        }

        val nextCardIndex = Random.nextInt(remainingCards.size)
        val card = remainingCards.removeAt(nextCardIndex)

        return card
    }

    fun startGame() {
        setDefaultDeck()
        shuffleDeck()

        do {
            userCards.add(drawCard())
        } while (calculateHandValue(userCards) > 21)

        do {
            targetCards.add(drawCard())
        } while (calculateHandValue(targetCards) > 21)
    }

    fun turn(user: BlackjackMultiplayerPlayers): BlackjackCardObject? {
        val card = drawCard()
        val cards: MutableList<BlackjackCardObject>;
        if (user === BlackjackMultiplayerPlayers.PLAYER) {
            userCards.add(card)
            cards = userCards
        } else {
            targetCards.add(card)
            cards = targetCards
        }

        if (calculateHandValue(cards) > 21) return null;
        if (turn == BlackjackMultiplayerPlayers.PLAYER) turn = BlackjackMultiplayerPlayers.TARGET else turn = BlackjackMultiplayerPlayers.PLAYER
        return card;
    }

    fun turn(): BlackjackCardObject? {
        return turn(turn)
    }

    fun stop(): BlackjackMultiplayerEndGameResultType {
        val userHand = calculateHandValue(userCards)
        val targetHand = calculateHandValue(targetCards)

        return when {
            userHand > targetHand -> BlackjackMultiplayerEndGameResultType.PLAYER
            userHand < targetHand -> BlackjackMultiplayerEndGameResultType.TARGET
            else -> BlackjackMultiplayerEndGameResultType.DRAW
        }
    }
}