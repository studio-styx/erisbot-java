package games.blackjack.core.singlePlayer

import shared.utils.Icon
import shared.utils.Utils

enum class Action {
    HIT,
    STAND
}

data class ActionResult(var action: Action, var card: BlackjackCardObject?) {
    fun getCard(): BlackjackCardObject? {
        return card
    }

    fun getAction(): Action {
        return action
    }

    fun setCard(card: BlackjackCardObject?): ActionResult = apply {
        this.card = card
    }

    fun setAction(action: Action): ActionResult = apply {
        this.action = action
    }
}

class BlackjackIA(private val game: BlackjackGame) {

    fun decideErisAction(): ActionResult {
        val playerVisibleCardValue = game.userCards[0].number
        val erisHand = game.calculateHandValue(game.erisCards)
        val isSoftHand = game.erisCards.any { it.card == "A" } && erisHand <= 18

        // Função auxiliar para escolher uma carta
        fun chooseCard(minValue: Int, maxValue: Int, chanceOfError: Double): BlackjackCardObject {
            val validCards = game.remainingCards.filter { card ->
                card.number in minValue..maxValue
            }
            val targetCards = if (chanceOfError > Math.random()) game.remainingCards else validCards
            return if (targetCards.isNotEmpty()) {
                targetCards.random()
            } else {
                game.remainingCards.random()
            }
        }

        // Dificuldade 0: Dealer padrão
        if (game.difficulty == 0) {
            return if (erisHand < 17 || (erisHand == 17 && isSoftHand)) {
                ActionResult(Action.HIT, null)
            } else {
                ActionResult(Action.STAND, null)
            }
        }

        // Calcular probabilidade base para pedir carta
        var chance = 0.6

        when (game.difficulty) {
            1 -> {
                // Eris Fácil: Decisão simples com base em probabilidade
                if (erisHand > 17) chance -= 0.4
                if (erisHand > 19) chance -= 0.3
                chance += getHumorModifier()
                chance = chance.coerceIn(0.0, 1.0)
                return if (Math.random() < chance) ActionResult(Action.HIT, null) else ActionResult(Action.STAND, null)
            }
            2 -> {
                // Eris Normal: Mais cautelosa, considera risco de estouro
                if (erisHand > 17) chance -= 0.4
                if (erisHand > 19) chance -= 0.28
                if (playerVisibleCardValue >= 10) chance += 0.17
                if (playerVisibleCardValue <= 6) chance += 0.17
                if (isSoftHand && erisHand <= 17) chance += 0.25

                // Calcula risco de estourar
                val bustRisk = game.remainingCards.count { card ->
                    card.number + erisHand > 21
                }.toDouble() / game.remainingCards.size
                if (bustRisk > 0.4 && erisHand >= 15) chance -= 0.15
                if (erisHand == 21) chance = 0.0
                chance = chance.coerceIn(0.0, 1.0)

                if (Math.random() > chance) {
                    return ActionResult(Action.STAND, null)
                }

                val maxCardValue = 21 - erisHand
                val card = chooseCard(1, maxCardValue, 0.4)
                return ActionResult(Action.HIT, card)
            }
            3 -> {
                // Eris Difícil: Mais estratégica, menor chance de erro
                if (erisHand > 17) chance -= 0.5
                if (erisHand > 19) chance -= 0.3
                if (playerVisibleCardValue >= 10) chance += 0.3
                if (playerVisibleCardValue <= 6) chance -= 0.3
                if (isSoftHand && erisHand <= 17) chance += 0.2

                chance = chance.coerceIn(0.0, 1.0)

                if (Math.random() > chance) {
                    return ActionResult(Action.STAND, null)
                }

                val maxCardValue = 21 - erisHand
                val card = chooseCard(1, maxCardValue, 0.2)
                return ActionResult(Action.HIT, card)
            }
            4 -> {
                // Eris Pesadelo: Altamente estratégica, considera probabilidade de estourar
                if (erisHand > 17) chance -= 0.6
                if (erisHand > 19) chance -= 0.4
                if (playerVisibleCardValue >= 10) chance += 0.4
                if (playerVisibleCardValue <= 6) chance -= 0.4
                if (isSoftHand && erisHand <= 17) chance += 0.3

                // Calcula probabilidade de estourar
                val bustRisk = game.remainingCards.count { card ->
                    card.number + erisHand > 21
                }.toDouble() / game.remainingCards.size
                if (bustRisk > 0.5 && erisHand >= 16) chance -= 0.3

                chance = chance.coerceIn(0.0, 1.0)

                if (Math.random() > chance) {
                    return ActionResult(Action.STAND, null)
                }

                val maxCardValue = 21 - erisHand
                val card = chooseCard(1, maxCardValue, 0.1)
                return ActionResult(Action.HIT, card)
            }
        }

        // Fallback para dificuldades inválidas
        return ActionResult(Action.STAND, null)
    }

    private fun chooseCard(minValue: Int, maxValue: Int, chanceOfError: Double): BlackjackCardObject {
        val validCards = game.remainingCards.filter { it.number in minValue..maxValue }
        val targetCards = if (chanceOfError > Math.random()) game.remainingCards else validCards
        return Utils.getRandomListValue(if (targetCards.isNotEmpty()) targetCards else game.remainingCards)
    }

    private fun getHumorModifier(): Double {
        return when (game.humor) {
            BlackjackErisMood.ANGRY -> 0.3
            BlackjackErisMood.HAPPY -> 0.1
            BlackjackErisMood.SAD -> -0.2
            BlackjackErisMood.NEUTRAL -> 0.0
            BlackjackErisMood.SCARED -> -0.3
            BlackjackErisMood.SURPRISED -> if (kotlin.random.Random.nextDouble() < 0.5) -0.1 else 0.1
            BlackjackErisMood.CONFUSED -> (Math.random() * 0.6) - 0.3
            else -> 0.0
        }
    }
}

enum class BlackjackLocale {
    PORTUGUESE,
    ENGLISH,
    SPANISH
}