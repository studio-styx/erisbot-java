package games.blackjack.core.singlePlayer

import kotlin.random.Random

class BlackjackGame(
    erisMood: BlackjackErisMood,
    difficulty: Int,
    amountAposted: Double
) {
    val erisMood: BlackjackErisMood = erisMood
    val difficulty: Int = difficulty
    val amountAposted: Double = amountAposted
    var humor: BlackjackErisMood = erisMood
        private set
    var erisCards: MutableList<BlackjackCardObject> = mutableListOf()
        private set
    var userCards: MutableList<BlackjackCardObject> = mutableListOf()
        private set
    var remainingCards: MutableList<BlackjackCardObject> = mutableListOf()
        private set
    var turnCount: Int = 0
    var passCount: Int = 0


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
            erisCards.add(drawCard())
            if (this.difficulty === 0) erisCards.add(drawCard())
        } while (calculateHandValue(erisCards) > 21)

        do {
            userCards.add(drawCard())
            if (this.difficulty === 0) userCards.add(drawCard())
        } while (calculateHandValue(userCards) > 21)
    }

    fun userTurn(): BlackjackCardObject? {
        val card = drawCard()
        userCards.add(card)

        if (calculateHandValue(userCards) > 21) return null;
        return card;
    }

    fun erisTurn(predefinedCard: BlackjackCardObject? = null): BlackjackCardObject? {
        val card = predefinedCard ?: drawCard();

        erisCards.add(card);

        if (calculateHandValue(erisCards) > 21) return null;
        return card;
    }

    fun stop(): BlackjackEndGameResultType {
        val userHand = calculateHandValue(userCards)
        val erisHand = calculateHandValue(erisCards)

        return when {
            userHand > erisHand -> BlackjackEndGameResultType.PLAYER
            userHand < erisHand -> BlackjackEndGameResultType.BOT
            else -> BlackjackEndGameResultType.DRAW
        }
    }
}