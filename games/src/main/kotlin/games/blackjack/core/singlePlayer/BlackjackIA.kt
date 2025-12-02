package games.blackjack.core.singlePlayer

import games.blackjack.core.dtos.BlackjackCardObject
import games.blackjack.core.dtos.ComentaryTraductions
import shared.utils.Icon
import shared.utils.Utils

enum class Action {
    HIT,
    STAND
}

data class ActionResult(var action: Action, var card: BlackjackCardObject?) {}

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
                chance = 0.7
                println("chance de pegar uma carta: $chance")
                if (erisHand > 17) chance -= 0.6
                if (erisHand > 19) chance -= 0.4
                if (playerVisibleCardValue >= 10) chance += 0.4
                if (playerVisibleCardValue <= 6) chance -= 0.4
                if (isSoftHand && erisHand <= 17) chance += 0.3

                // Calcula probabilidade de estourar
                val bustRisk = game.remainingCards.count { card ->
                    card.number + erisHand > 21
                }.toDouble() / game.remainingCards.size
                if (bustRisk > 0.5 && erisHand >= 16) chance -= 0.25
                if (erisHand < 16) chance += 0.3
                // alterar um pouco as chances de pegar uma carta
                val chanceOfError = Utils.getRandomDouble(-0.1, 0.1)
                chance += chanceOfError
                if (erisHand == 21) chance = 0.0

                chance = chance.coerceIn(0.0, 1.0)

                if (chance > 90 || Math.random() > chance) {
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

    fun getErisComentary(): ComentaryTraductions {
        return getErisComentary(null)
    }
    fun getErisComentary(wins: BlackjackEndGameResultType?): ComentaryTraductions {
        val erisHand = game.calculateHandValue(game.erisCards);
        val userHand = game.calculateHandValue(game.userCards);
        val humorModifier = this.getHumorModifier();

        val isUserBusted = userHand > 21;
        val isErisNearBust = erisHand >= 19 && erisHand < 21;
        val isErisWeakHand = game.erisCards.size >= 3 && erisHand <= 17;
        val shouldBluff = isErisWeakHand && Math.random() < 0.7; // 70% de chance de blefar

        val eventMessages: Map<String, List<ComentaryTraductions>> = mapOf(
            "erisWins" to listOf(
                ComentaryTraductions(
                    "${Icon.static.get("Eris_happy")} Haha, vitória minha! Melhor sorte na próxima!", // português
                    "${Icon.static.get("Eris_happy")} Haha, my victory! Better luck next time!", // inglês (Placeholder)
                    "${Icon.static.get("Eris_happy")} Jaja, ¡victoria mía! ¡Mejor suerte la próxima vez!" // espanhol (Placeholder)
                ),
                ComentaryTraductions(
                    "${Icon.static.get("Eris_kiss_left")} Ganhei, e com estilo!", // português
                    "${Icon.static.get("Eris_kiss_left")} I won, and with style!", // inglês (Placeholder)
                    "${Icon.static.get("Eris_kiss_left")} ¡Gané, y con estilo!" // espanhol (Placeholder)
                ),
                ComentaryTraductions(
                    "${Icon.static.get("Eris_trusting")} Sabia que eu era imbatível!", // português
                    "${Icon.static.get("Eris_trusting")} I knew I was unbeatable!", // inglês (Placeholder)
                    "${Icon.static.get("Eris_trusting")} ¡Sabía que era imbatible!" // espanhol (Placeholder)
                ),
                ComentaryTraductions(
                    "${Icon.static.get("Eris_ok")} Você tentou, mas eu sou melhor!", // português
                    "${Icon.static.get("Eris_ok")} You tried, but I'm better!", // inglês (Placeholder)
                    "${Icon.static.get("Eris_ok")} ¡Lo intentaste, pero soy mejor!" // espanhol (Placeholder)
                ),
                ComentaryTraductions(
                    "Minha mão arrasou! ${Icon.static.get("Eris_enchanted")}", // português
                    "My hand rocked! ${Icon.static.get("Eris_enchanted")}", // inglês (Placeholder)
                    "¡Mi mano arrasó! ${Icon.static.get("Eris_enchanted")}" // espanhol (Placeholder)
                )
            ),
            "erisLoses" to listOf(
                ComentaryTraductions(
                    "${Icon.static.get("Eris_cry")} Droga, como você conseguiu isso?!", // português
                    "${Icon.static.get("Eris_cry")} Damn, how did you manage that?!", // inglês (Placeholder)
                    "${Icon.static.get("Eris_cry")} ¡Maldición, ¿cómo lograste eso?!" // espanhol (Placeholder)
                ),
                ComentaryTraductions(
                    "${Icon.static.get("Eris_shy_left")} Perdi... mas foi por pouco!", // português
                    "${Icon.static.get("Eris_shy_left")} I lost... but it was close!", // inglês (Placeholder)
                    "${Icon.static.get("Eris_shy_left")} Perdí... ¡pero fue por poco!" // espanhol (Placeholder)
                ),
                ComentaryTraductions(
                    "${Icon.static.get("Eris_Angry")} Isso não acaba aqui, humano!", // português
                    "${Icon.static.get("Eris_Angry")} This doesn't end here, human!", // inglês (Placeholder)
                    "${Icon.static.get("Eris_Angry")} ¡Esto no termina aquí, humano!" // espanhol (Placeholder)
                ),
                ComentaryTraductions(
                    "${Icon.static.get("Eris_cry_left")} Minha mão me traiu...", // português
                    "${Icon.static.get("Eris_cry_left")} My hand betrayed me...", // inglês (Placeholder)
                    "${Icon.static.get("Eris_cry_left")} Mi mano me traicionó..." // espanhol (Placeholder)
                ),
                ComentaryTraductions(
                    "Tch, parabéns... por enquanto. ${Icon.static.get("Eris_thinking")}", // português
                    "Tch, congratulations... for now. ${Icon.static.get("Eris_thinking")}", // inglês (Placeholder)
                    "Tch, felicidades... por ahora. ${Icon.static.get("Eris_thinking")}" // espanhol (Placeholder)
                )
            ),
            "push" to listOf(
                ComentaryTraductions(
                    "${Icon.static.get("Eris_fair")} Empate? Nada mal, né?", // português
                    "${Icon.static.get("Eris_fair")} Push? Not bad, right?", // inglês (Placeholder)
                    "${Icon.static.get("Eris_fair")} ¿Empate? No está mal, ¿verdad?" // espanhol (Placeholder)
                ),
                ComentaryTraductions(
                    "${Icon.static.get("Eris_ok_left")} Ninguém ganha, ninguém perde!", // português
                    "${Icon.static.get("Eris_ok_left")} Nobody wins, nobody loses!", // inglês (Placeholder)
                    "${Icon.static.get("Eris_ok_left")} ¡Nadie gana, nadie pierde!" // espanhol (Placeholder)
                ),
                ComentaryTraductions(
                    "${Icon.static.get("Eris_thinking")} Empate... que equilíbrio chato!", // português
                    "${Icon.static.get("Eris_thinking")} Push... what a boring balance!", // inglês (Placeholder)
                    "${Icon.static.get("Eris_thinking")} Empate... ¡qué equilibrio aburrido!" // espanhol (Placeholder)
                ),
                ComentaryTraductions(
                    "${Icon.static.get("Eris_fair_left")} Empatamos, mas eu quase te peguei!", // português
                    "${Icon.static.get("Eris_fair_left")} We pushed, but I almost got you!", // inglês (Placeholder)
                    "${Icon.static.get("Eris_fair_left")} Empatamos, ¡pero casi te atrapo!" // espanhol (Placeholder)
                ),
                ComentaryTraductions(
                    "Hmph, empate. Vamos de novo? ${Icon.static.get("Eris_trusting_left")}", // português
                    "Hmph, push. Shall we go again? ${Icon.static.get("Eris_trusting_left")}", // inglês (Placeholder)
                    "Hmph, empate. ¿Vamos de nuevo? ${Icon.static.get("Eris_trusting_left")}" // espanhol (Placeholder)
                )
            ),
            "userBusted" to listOf(
                ComentaryTraductions(
                    "${Icon.static.get("Eris_happy")} Estourou, hein? Minha vitória!", // português
                    "${Icon.static.get("Eris_happy")} Busted, huh? My victory!", // inglês (Placeholder)
                    "${Icon.static.get("Eris_happy")} ¿Te pasaste, eh? ¡Mi victoria!" // espanhol (Placeholder)
                ),
                ComentaryTraductions(
                    "${Icon.static.get("Eris_kiss")} Mais de 21? Tô rindo!", // português
                    "${Icon.static.get("Eris_kiss")} Over 21? I'm laughing!", // inglês (Placeholder)
                    "${Icon.static.get("Eris_kiss")} ¿Más de 21? ¡Me estoy riendo!" // espanhol (Placeholder)
                ),
                ComentaryTraductions(
                    "${Icon.static.get("Eris_ok_left")} Você estourou, que pena!", // português
                    "${Icon.static.get("Eris_ok_left")} You busted, too bad!", // inglês (Placeholder)
                    "${Icon.static.get("Eris_ok_left")} Te pasaste, ¡qué lástima!" // espanhol (Placeholder)
                ),
                ComentaryTraductions(
                    "${Icon.static.get("Eris_trusting")} Estourar é triste, né? Ganhei!", // português
                    "${Icon.static.get("Eris_trusting")} Busting is sad, isn't it? I won!", // inglês (Placeholder)
                    "${Icon.static.get("Eris_trusting")} Pasarse es triste, ¿verdad? ¡Gané!" // espanhol (Placeholder)
                ),
                ComentaryTraductions(
                    "Tá vendo? Não dá pra competir comigo! ${Icon.static.get("Eris_happy_left")}", // português
                    "See? You can't compete with me! ${Icon.static.get("Eris_happy_left")}", // inglês (Placeholder)
                    "¿Ves? ¡No puedes competir conmigo! ${Icon.static.get("Eris_happy_left")}" // espanhol (Placeholder)
                )
            ),
            "erisNearBust" to listOf(
                ComentaryTraductions(
                    "${Icon.static.get("Eris_shy")} Tô no limite aqui...", // português
                    "${Icon.static.get("Eris_shy")} I'm at the limit here...", // inglês (Placeholder)
                    "${Icon.static.get("Eris_shy")} Estoy al límite aquí..." // espanhol (Placeholder)
                ),
                ComentaryTraductions(
                    "${Icon.static.get("Eris_thinking_left")} Essas cartas tão perigosas!", // português
                    "${Icon.static.get("Eris_thinking_left")} These cards are dangerous!", // inglês (Placeholder)
                    "${Icon.static.get("Eris_thinking_left")} ¡Estas cartas son peligrosas!" // espanhol (Placeholder)
                ),
                ComentaryTraductions(
                    "${Icon.static.get("Eris_cry")} Quase estourando, que medo!", // português
                    "${Icon.static.get("Eris_cry")} Almost busting, I'm scared!", // inglês (Placeholder)
                    "${Icon.static.get("Eris_cry")} ¡Casi me paso, qué miedo!" // espanhol (Placeholder)
                ),
                ComentaryTraductions(
                    "${Icon.static.get("Eris_shy_left")} Minha mão tá pesada...", // português
                    "${Icon.static.get("Eris_shy_left")} My hand is heavy...", // inglês (Placeholder)
                    "${Icon.static.get("Eris_shy_left")} Mi mano está pesada..." // espanhol (Placeholder)
                ),
                ComentaryTraductions(
                    "Tô na corda bamba com essa mão! ${Icon.static.get("Eris_thinking")}", // português
                    "I'm on thin ice with this hand! ${Icon.static.get("Eris_thinking")}", // inglês (Placeholder)
                    "¡Estoy en la cuerda floja con esta mano! ${Icon.static.get("Eris_thinking")}" // espanhol (Placeholder)
                )
            ),
            "erisWeakBluff" to listOf(
                ComentaryTraductions(
                    "${Icon.static.get("Eris_trusting")} Minha mão tá imbatível, pode desistir!", // português
                    "${Icon.static.get("Eris_trusting")} My hand is unbeatable, you can give up!", // inglês (Placeholder)
                    "${Icon.static.get("Eris_trusting")} ¡Mi mano es imbatible, puedes rendirte!" // espanhol (Placeholder)
                ),
                ComentaryTraductions(
                    "${Icon.static.get("Eris_happy_left")} Acho que já tenho 21, hein!", // português
                    "${Icon.static.get("Eris_happy_left")} I think I already have 21, huh!", // inglês (Placeholder)
                    "${Icon.static.get("Eris_happy_left")} ¡Creo que ya tengo 21, eh!" // espanhol (Placeholder)
                ),
                ComentaryTraductions(
                    "${Icon.static.get("Eris_kiss")} Essas cartas são puro ouro!", // português
                    "${Icon.static.get("Eris_kiss")} These cards are pure gold!", // inglês (Placeholder)
                    "${Icon.static.get("Eris_kiss")} ¡Estas cartas son oro puro!" // espanhol (Placeholder)
                ),
                ComentaryTraductions(
                    "${Icon.static.get("Eris_ok")} Você não vai querer enfrentar essa mão!", // português
                    "${Icon.static.get("Eris_ok")} You won't want to face this hand!", // inglês (Placeholder)
                    "${Icon.static.get("Eris_ok")} ¡No querrás enfrentarte a esta mano!" // espanhol (Placeholder)
                ),
                ComentaryTraductions(
                    "Tô com uma mão perfeita, cuidado! ${Icon.static.get("Eris_enchanted_left")}", // português
                    "I have a perfect hand, beware! ${Icon.static.get("Eris_enchanted_left")}", // inglês (Placeholder)
                    "¡Tengo una mano perfecta, cuidado! ${Icon.static.get("Eris_enchanted_left")}" // espanhol (Placeholder)
                )
            ),
            "erisWeakTruth" to listOf(
                ComentaryTraductions(
                    "${Icon.static.get("Eris_shy")} Essas cartas não tão ajudando...", // português
                    "${Icon.static.get("Eris_shy")} These cards aren't helping...", // inglês (Placeholder)
                    "${Icon.static.get("Eris_shy")} Estas cartas no están ayudando..." // espanhol (Placeholder)
                ),
                ComentaryTraductions(
                    "${Icon.static.get("Eris_cry_left")} Minha mão tá meio fraca...", // português
                    "${Icon.static.get("Eris_cry_left")} My hand is a bit weak...", // inglês (Placeholder)
                    "${Icon.static.get("Eris_cry_left")} Mi mano está un poco débil..." // espanhol (Placeholder)
                ),
                ComentaryTraductions(
                    "${Icon.static.get("Eris_thinking")} Não sei se isso vai dar certo...", // português
                    "${Icon.static.get("Eris_thinking")} I don't know if this will work...", // inglês (Placeholder)
                    "${Icon.static.get("Eris_thinking")} No sé si esto funcionará..." // espanhol (Placeholder)
                ),
                ComentaryTraductions(
                    "${Icon.static.get("Eris_shy_left")} Tô com uma mão ruim, né?", // português
                    "${Icon.static.get("Eris_shy_left")} I have a bad hand, right?", // inglês (Placeholder)
                    "${Icon.static.get("Eris_shy_left")} Tengo una mala mano, ¿verdad?" // espanhol (Placeholder)
                ),
                ComentaryTraductions(
                    "Essas cartas tão me complicando... ${Icon.static.get("Eris_cry")}", // português
                    "These cards are complicating things for me... ${Icon.static.get("Eris_cry")}", // inglês (Placeholder)
                    "Estas cartas me están complicando... ${Icon.static.get("Eris_cry")}" // espanhol (Placeholder)
                )
            )
        )

        if (wins == BlackjackEndGameResultType.BOT) {
            return eventMessages["erisWins"]!!.random();
        }
        if (wins === BlackjackEndGameResultType.PLAYER) {
            return eventMessages["erisLoses"]!!.random();
        }
        if (wins === BlackjackEndGameResultType.DRAW) {
            return eventMessages["push"]!!.random();
        }
        if (isUserBusted) {
            return eventMessages["userBusted"]!!.random();
        }
        if (isErisNearBust) {
            return eventMessages["erisNearBust"]!!.random();
        }
        if (isErisWeakHand) {
            return if (shouldBluff) eventMessages["erisWeakBluff"]!!.random()
                else eventMessages["erisWeakTruth"]!!.random();
        }

        var polary: Double = 0.5 // Valor inicial de exemplo, ajuste conforme necessário

        // 1. Modificador baseado na primeira carta do jogador
        if (game.userCards.isNotEmpty()) {
            when (game.userCards[0].card) {
                // Jogador tem carta alta
                "10", "J", "Q", "K", "A" -> polary -= 0.3
                // Jogador tem carta baixa
                "2", "3", "4", "5", "6" -> polary += 0.3
            }
        }

        // 2. Modificador baseado na mão de Eris
        if (erisHand > 17) polary -= 0.2
        if (erisHand > 19) polary -= 0.2

        // 3. Aplicar modificador de humor
        polary += humorModifier

        val sentimento: String = when {
            polary > 0.7 -> "confiante"
            polary < 0.3 -> "insegura"
            else -> "neutra"
        }

        val messages: Map<BlackjackErisMood, Map<MoodType, List<ComentaryTraductions>>> = mapOf(
            BlackjackErisMood.ANGRY to mapOf(
                MoodType.CONFIDENT to listOf(
                    ComentaryTraductions(
                        "${Icon.static.get("Eris_Angry")} Minha mão vai te destruir!",
                        "${Icon.static.get("Eris_Angry")} My hand is going to destroy you!", // Placeholder
                        "${Icon.static.get("Eris_Angry")} ¡Mi mano te va a destruir!" // Placeholder
                    ),
                    ComentaryTraductions(
                        "${Icon.static.get("Eris_Angry_left")} Tô pronta pra te humilhar!",
                        "${Icon.static.get("Eris_Angry_left")} I'm ready to humiliate you!", // Placeholder
                        "${Icon.static.get("Eris_Angry_left")} ¡Estoy lista para humillarte!" // Placeholder
                    ),
                    ComentaryTraductions(
                        "Suas cartas não têm chance contra mim!",
                        "Your cards don't stand a chance against me!", // Placeholder
                        "¡Tus cartas no tienen ninguna oportunidad contra mí!" // Placeholder
                    ),
                    ComentaryTraductions(
                        "${Icon.static.get("Eris_ok")} Pode vir, eu topo!",
                        "${Icon.static.get("Eris_ok")} Come on, I'm in!", // Placeholder
                        "${Icon.static.get("Eris_ok")} ¡Puedes venir, acepto!" // Placeholder
                    ),
                    ComentaryTraductions(
                        "Essa rodada é minha! ${Icon.static.get("Eris_Angry")}",
                        "This round is mine! ${Icon.static.get("Eris_Angry")}", // Placeholder
                        "¡Esta ronda es mía! ${Icon.static.get("Eris_Angry")}" // Placeholder
                    )
                ),
                MoodType.NEUTRAL to listOf(
                    ComentaryTraductions(
                        "Não me subestime.",
                        "Don't underestimate me.", // Placeholder
                        "No me subestimes." // Placeholder
                    ),
                    ComentaryTraductions(
                        "${Icon.static.get("Eris_thinking_left")} Vamos ver o que você faz...",
                        "${Icon.static.get("Eris_thinking_left")} Let's see what you do...", // Placeholder
                        "${Icon.static.get("Eris_thinking_left")} Veamos qué haces..." // Placeholder
                    ),
                    ComentaryTraductions(
                        "${Icon.static.get("Eris_Angry")} Hmph, joga logo!",
                        "${Icon.static.get("Eris_Angry")} Hmph, just play!", // Placeholder
                        "${Icon.static.get("Eris_Angry")} Hmph, ¡juega de una vez!" // Placeholder
                    ),
                    ComentaryTraductions(
                        "Tô esperando sua jogada. ${Icon.static.get("Eris_ok_left")}",
                        "I'm waiting for your move. ${Icon.static.get("Eris_ok_left")}", // Placeholder
                        "Estoy esperando tu jugada. ${Icon.static.get("Eris_ok_left")}" // Placeholder
                    ),
                    ComentaryTraductions(
                        "Não me faça perder a paciência!",
                        "Don't make me lose my patience!", // Placeholder
                        "¡No me hagas perder la paciencia!" // Placeholder
                    )
                ),
                MoodType.INSECURE to listOf(
                    ComentaryTraductions(
                        "Tch... essas cartas são um lixo.",
                        "Tch... these cards are trash.", // Placeholder
                        "Tch... estas cartas son basura." // Placeholder
                    ),
                    ComentaryTraductions(
                        "${Icon.static.get("Eris_Angry_left")} Sua sorte não dura pra sempre!",
                        "${Icon.static.get("Eris_Angry_left")} Your luck doesn't last forever!", // Placeholder
                        "${Icon.static.get("Eris_Angry_left")} ¡Tu suerte no dura para siempre!" // Placeholder
                    ),
                    ComentaryTraductions(
                        "Você tá me irritando com essa sorte! ${Icon.static.get("Eris_Angry")}",
                        "You're irritating me with this luck! ${Icon.static.get("Eris_Angry")}", // Placeholder
                        "¡Me estás irritando con esta suerte! ${Icon.static.get("Eris_Angry")}" // Placeholder
                    ),
                    ComentaryTraductions(
                        "${Icon.static.get("Eris_cry")} Essas cartas tão contra mim...",
                        "${Icon.static.get("Eris_cry")} These cards are against me...", // Placeholder
                        "${Icon.static.get("Eris_cry")} Estas cartas están en mi contra..." // Placeholder
                    ),
                    ComentaryTraductions(
                        "Vou virar isso, só espera!",
                        "I'm going to turn this around, just wait!", // Placeholder
                        "¡Voy a darle la vuelta a esto, solo espera!" // Placeholder
                    )
                )
            ),
            BlackjackErisMood.HAPPY to mapOf(
                MoodType.CONFIDENT to listOf(
                    ComentaryTraductions(
                        "${Icon.static.get("Eris_enchanted")} Tô com uma mão incrível!",
                        "${Icon.static.get("Eris_enchanted")} I have an incredible hand!", // Placeholder
                        "${Icon.static.get("Eris_enchanted")} ¡Tengo una mano increíble!" // Placeholder
                    ),
                    ComentaryTraductions(
                        "Essa rodada tá divertida demais! ${Icon.static.get("Eris_happy")}",
                        "This round is too much fun! ${Icon.static.get("Eris_happy")}", // Placeholder
                        "¡Esta ronda es demasiado divertida! ${Icon.static.get("Eris_happy")}" // Placeholder
                    ),
                    ComentaryTraductions(
                        "Prepare-se pra perder com estilo! ${Icon.static.get("Eris_kiss_left")}",
                        "Prepare to lose with style! ${Icon.static.get("Eris_kiss_left")}", // Placeholder
                        "¡Prepárate para perder con estilo! ${Icon.static.get("Eris_kiss_left")}" // Placeholder
                    ),
                    ComentaryTraductions(
                        "${Icon.static.get("Eris_trusting")} Minhas cartas são perfeitas!",
                        "${Icon.static.get("Eris_trusting")} My cards are perfect!", // Placeholder
                        "${Icon.static.get("Eris_trusting")} ¡Mis cartas son perfectas!" // Placeholder
                    ),
                    ComentaryTraductions(
                        "Tô amando essa partida! ${Icon.static.get("Eris_happy_left")}",
                        "I'm loving this match! ${Icon.static.get("Eris_happy_left")}", // Placeholder
                        "¡Me encanta esta partida! ${Icon.static.get("Eris_happy_left")}" // Placeholder
                    )
                ),
                MoodType.NEUTRAL to listOf(
                    ComentaryTraductions(
                        "Vamos ver no que dá~ ${Icon.static.get("Eris_fair")}",
                        "Let's see what happens~ ${Icon.static.get("Eris_fair")}", // Placeholder
                        "Veamos qué pasa~ ${Icon.static.get("Eris_fair")}" // Placeholder
                    ),
                    ComentaryTraductions(
                        "Hehe, sua vez!",
                        "Hehe, your turn!", // Placeholder
                        "¡Jeje, tu turno!" // Placeholder
                    ),
                    ComentaryTraductions(
                        "${Icon.static.get("Eris_ok")} Tô de olho em você...",
                        "${Icon.static.get("Eris_ok")} I'm watching you...", // Placeholder
                        "${Icon.static.get("Eris_ok")} Te estoy vigilando..." // Placeholder
                    ),
                    ComentaryTraductions(
                        "Essa rodada tá boa, né? ${Icon.static.get("Eris_happy")}",
                        "This round is good, right? ${Icon.static.get("Eris_happy")}", // Placeholder
                        "Esta ronda está buena, ¿verdad? ${Icon.static.get("Eris_happy")}" // Placeholder
                    ),
                    ComentaryTraductions(
                        "Jogando com calma, mas com estilo! ${Icon.static.get("Eris_kiss")}",
                        "Playing calmly, but with style! ${Icon.static.get("Eris_kiss")}", // Placeholder
                        "¡Jugando con calma, pero con estilo! ${Icon.static.get("Eris_kiss")}" // Placeholder
                    )
                ),
                MoodType.INSECURE to listOf(
                    ComentaryTraductions(
                        "Talvez eu não esteja tão bem... ${Icon.static.get("Eris_shy_left")}",
                        "Maybe I'm not doing so well... ${Icon.static.get("Eris_shy_left")}", // Placeholder
                        "Quizás no estoy tan bien... ${Icon.static.get("Eris_shy_left")}" // Placeholder
                    ),
                    ComentaryTraductions(
                        "Hmm... essas cartas são estranhas. ${Icon.static.get("Eris_thinking")}",
                        "Hmm... these cards are strange. ${Icon.static.get("Eris_thinking")}", // Placeholder
                        "Hmm... estas cartas son extrañas. ${Icon.static.get("Eris_thinking")}" // Placeholder
                    ),
                    ComentaryTraductions(
                        "${Icon.static.get("Eris_cry_left")} Ai, será que vou perder?",
                        "${Icon.static.get("Eris_cry_left")} Oh, am I going to lose?", // Placeholder
                        "${Icon.static.get("Eris_cry_left")} Ay, ¿será que voy a perder?" // Placeholder
                    ),
                    ComentaryTraductions(
                        "Tô meio preocupada aqui... ${Icon.static.get("Eris_shy")}",
                        "I'm a little worried here... ${Icon.static.get("Eris_shy")}", // Placeholder
                        "Estoy un poco preocupada aquí... ${Icon.static.get("Eris_shy")}" // Placeholder
                    ),
                    ComentaryTraductions(
                        "Minha mão não tá cooperando...",
                        "My hand is not cooperating...", // Placeholder
                        "Mi mano no está cooperando..." // Placeholder
                    )
                )
            ),
            BlackjackErisMood.SAD to mapOf(
                MoodType.CONFIDENT to listOf(
                    ComentaryTraductions(
                        "Pelo menos minhas cartas não são tão ruins... ${Icon.static.get("Eris_trusting_left")}",
                        "At least my cards aren't that bad... ${Icon.static.get("Eris_trusting_left")}", // Placeholder
                        "Al menos mis cartas no son tan malas... ${Icon.static.get("Eris_trusting_left")}" // Placeholder
                    ),
                    ComentaryTraductions(
                        "Ainda posso virar isso... ${Icon.static.get("Eris_ok")}",
                        "I can still turn this around... ${Icon.static.get("Eris_ok")}", // Placeholder
                        "Aún puedo darle la vuelta a esto... ${Icon.static.get("Eris_ok")}" // Placeholder
                    ),
                    ComentaryTraductions(
                        "${Icon.static.get("Eris_fair")} Tô tentando, tá?",
                        "${Icon.static.get("Eris_fair")} I'm trying, okay?", // Placeholder
                        "${Icon.static.get("Eris_fair")} Estoy intentando, ¿vale?" // Placeholder
                    ),
                    ComentaryTraductions(
                        "Um pouco de esperança nessa mão...",
                        "A little bit of hope in this hand...", // Placeholder
                        "Un poco de esperanza en esta mano..." // Placeholder
                    ),
                    ComentaryTraductions(
                        "Talvez eu consiga algo bom! ${Icon.static.get("Eris_happy")}",
                        "Maybe I'll get something good! ${Icon.static.get("Eris_happy")}", // Placeholder
                        "¡Quizás consiga algo bueno! ${Icon.static.get("Eris_happy")}" // Placeholder
                    )
                ),
                MoodType.NEUTRAL to listOf(
                    ComentaryTraductions(
                        "Tanto faz o resultado... ${Icon.static.get("Eris_cry")}",
                        "Whatever the outcome... ${Icon.static.get("Eris_cry")}", // Placeholder
                        "Lo que sea el resultado... ${Icon.static.get("Eris_cry")}" // Placeholder
                    ),
                    ComentaryTraductions(
                        "...",
                        "...", // Placeholder
                        "..." // Placeholder
                    ),
                    ComentaryTraductions(
                        "Jogando por jogar, né? ${Icon.static.get("Eris_shy_left")}",
                        "Just playing for the sake of it, right? ${Icon.static.get("Eris_shy_left")}", // Placeholder
                        "Jugando por jugar, ¿verdad? ${Icon.static.get("Eris_shy_left")}" // Placeholder
                    ),
                    ComentaryTraductions(
                        "Vamos acabar logo com isso...",
                        "Let's just get this over with...", // Placeholder
                        "Acabemos con esto de una vez..." // Placeholder
                    ),
                    ComentaryTraductions(
                        "Minhas cartas não me animam. ${Icon.static.get("Eris_cry_left")}",
                        "My cards don't cheer me up. ${Icon.static.get("Eris_cry_left")}", // Placeholder
                        "Mis cartas no me animan. ${Icon.static.get("Eris_cry_left")}" // Placeholder
                    )
                ),
                MoodType.INSECURE to listOf(
                    ComentaryTraductions(
                        "Eu sabia que ia dar errado... ${Icon.static.get("Eris_cry")}",
                        "I knew it was going to go wrong... ${Icon.static.get("Eris_cry")}", // Placeholder
                        "Sabía que iba a salir mal... ${Icon.static.get("Eris_cry")}" // Placeholder
                    ),
                    ComentaryTraductions(
                        "Por que sempre eu? ${Icon.static.get("Eris_shy")}",
                        "Why always me? ${Icon.static.get("Eris_shy")}", // Placeholder
                        "¿Por qué siempre yo? ${Icon.static.get("Eris_shy")}" // Placeholder
                    ),
                    ComentaryTraductions(
                        "${Icon.static.get("Eris_cry_left")} Essas cartas são horríveis...",
                        "${Icon.static.get("Eris_cry_left")} These cards are awful...", // Placeholder
                        "${Icon.static.get("Eris_cry_left")} Estas cartas son horribles..." // Placeholder
                    ),
                    ComentaryTraductions(
                        "Nada dá certo pra mim...",
                        "Nothing works out for me...", // Placeholder
                        "Nada me sale bien..." // Placeholder
                    ),
                    ComentaryTraductions(
                        "Minha mão é uma tristeza. ${Icon.static.get("Eris_shy_left")}",
                        "My hand is a sadness. ${Icon.static.get("Eris_shy_left")}", // Placeholder
                        "Mi mano es una tristeza. ${Icon.static.get("Eris_shy_left")}" // Placeholder
                    )
                )
            ),
            BlackjackErisMood.NEUTRAL to mapOf(
                        MoodType.CONFIDENT to listOf(
                            ComentaryTraductions(
                                "Minha mão tá bem sólida. ${Icon.static.get("Eris_ok")}",
                                "My hand is quite solid. ${Icon.static.get("Eris_ok")}", // Placeholder
                                "¡Mi mano está muy sólida! ${Icon.static.get("Eris_ok")}" // Placeholder
                            ),
                            ComentaryTraductions(
                                "Tô gostando dessas cartas. ${Icon.static.get("Eris_trusting")}",
                                "I'm liking these cards. ${Icon.static.get("Eris_trusting")}", // Placeholder
                                "Me están gustando estas cartas. ${Icon.static.get("Eris_trusting")}" // Placeholder
                            ),
                            ComentaryTraductions(
                                "${Icon.static.get("Eris_fair_left")} Vamos ver quem leva essa!",
                                "${Icon.static.get("Eris_fair_left")} Let's see who takes this one!", // Placeholder
                                "${Icon.static.get("Eris_fair_left")} ¡Vamos a ver quién se lleva esta!" // Placeholder
                            ),
                            ComentaryTraductions(
                                "Aposta alta? Eu topo! ${Icon.static.get("Eris_ok_left")}",
                                "High bet? I'm in! ${Icon.static.get("Eris_ok_left")}", // Placeholder
                                "¿Apuesta alta? ¡Acepto! ${Icon.static.get("Eris_ok_left")}" // Placeholder
                            ),
                            ComentaryTraductions(
                                "Tô pronta pra essa rodada!",
                                "I'm ready for this round!", // Placeholder
                                "¡Estoy lista para esta ronda!" // Placeholder
                            )
                        ),
                MoodType.NEUTRAL to listOf(
                    ComentaryTraductions(
                        "Continuando o jogo... ${Icon.static.get("Eris_thinking")}",
                        "Continuing the game... ${Icon.static.get("Eris_thinking")}", // Placeholder
                        "Continuando el juego... ${Icon.static.get("Eris_thinking")}" // Placeholder
                    ),
                    ComentaryTraductions(
                        "Hmm...",
                        "Hmm...", // Placeholder
                        "Hmm..." // Placeholder
                    ),
                    ComentaryTraductions(
                        "Vamos lá, sua vez. ${Icon.static.get("Eris_fair")}",
                        "Come on, your turn. ${Icon.static.get("Eris_fair")}", // Placeholder
                        "Vamos, tu turno. ${Icon.static.get("Eris_fair")}" // Placeholder
                    ),
                    ComentaryTraductions(
                        "Sem pressa, só jogando... ${Icon.static.get("Eris_ok")}",
                        "No rush, just playing... ${Icon.static.get("Eris_ok")}", // Placeholder
                        "Sin prisa, solo jugando... ${Icon.static.get("Eris_ok")}" // Placeholder
                    ),
                    ComentaryTraductions(
                        "O que você tem aí? ${Icon.static.get("Eris_thinking_left")}",
                        "What do you have there? ${Icon.static.get("Eris_thinking_left")}", // Placeholder
                        "¿Qué tienes ahí? ${Icon.static.get("Eris_thinking_left")}" // Placeholder
                    )
                ),
                MoodType.INSECURE to listOf(
                    ComentaryTraductions(
                        "Isso pode dar ruim... ${Icon.static.get("Eris_shy")}",
                        "This could go wrong... ${Icon.static.get("Eris_shy")}", // Placeholder
                        "Esto podría salir mal... ${Icon.static.get("Eris_shy")}" // Placeholder
                    ),
                    ComentaryTraductions(
                        "Vamos ver no que dá...",
                        "Let's see what happens...", // Placeholder
                        "Vamos a ver qué pasa..." // Placeholder
                    ),
                    ComentaryTraductions(
                        "Minha mão não tá tão boa. ${Icon.static.get("Eris_cry")}",
                        "My hand is not that good. ${Icon.static.get("Eris_cry")}", // Placeholder
                        "Mi mano no es tan buena. ${Icon.static.get("Eris_cry")}" // Placeholder
                    ),
                    ComentaryTraductions(
                        "${Icon.static.get("Eris_thinking")} Será que fiz a escolha certa?",
                        "${Icon.static.get("Eris_thinking")} Did I make the right choice?", // Placeholder
                        "${Icon.static.get("Eris_thinking")} ¿Hice la elección correcta?" // Placeholder
                    ),
                    ComentaryTraductions(
                        "Tô meio preocupada com essa rodada... ${Icon.static.get("Eris_shy_left")}",
                        "I'm a little worried about this round... ${Icon.static.get("Eris_shy_left")}", // Placeholder
                        "Estoy un poco preocupada por esta ronda... ${Icon.static.get("Eris_shy_left")}" // Placeholder
                    )
                )
            ),
            BlackjackErisMood.SCARED to mapOf(
                MoodType.CONFIDENT to listOf(
                    ComentaryTraductions(
                        "Acho que posso ganhar essa! ${Icon.static.get("Eris_trusting")}",
                        "I think I can win this one! ${Icon.static.get("Eris_trusting")}", // Placeholder
                        "¡Creo que puedo ganar esta! ${Icon.static.get("Eris_trusting")}" // Placeholder
                    ),
                    ComentaryTraductions(
                        "Minha mão tá... ok, né? ${Icon.static.get("Eris_ok_left")}",
                        "My hand is... okay, right? ${Icon.static.get("Eris_ok_left")}", // Placeholder
                        "Mi mano está... bien, ¿verdad? ${Icon.static.get("Eris_ok_left")}" // Placeholder
                    ),
                    ComentaryTraductions(
                        "${Icon.static.get("Eris_happy")} Ufa, essas cartas são boas!",
                        "${Icon.static.get("Eris_happy")} Phew, these cards are good!", // Placeholder
                        "${Icon.static.get("Eris_happy")} ¡Uf, estas cartas son buenas!" // Placeholder
                    ),
                    ComentaryTraductions(
                        "Tô quase lá, só não estraga!",
                        "I'm almost there, just don't spoil it!", // Placeholder
                        "Estoy casi ahí, ¡solo no lo estropees!" // Placeholder
                    ),
                    ComentaryTraductions(
                        "Será que é minha sorte? ${Icon.static.get("Eris_enchanted")}",
                        "Is it my luck? ${Icon.static.get("Eris_enchanted")}", // Placeholder
                        "¿Será mi suerte? ${Icon.static.get("Eris_enchanted")}" // Placeholder
                    )
                ),
                MoodType.NEUTRAL to listOf(
                    ComentaryTraductions(
                        "Ai, ai... ${Icon.static.get("Eris_shy")}",
                        "Oh dear... ${Icon.static.get("Eris_shy")}", // Placeholder
                        "Ay, ay... ${Icon.static.get("Eris_shy")}" // Placeholder
                    ),
                    ComentaryTraductions(
                        "Tomara que dê certo... ${Icon.static.get("Eris_thinking_left")}",
                        "I hope it works out... ${Icon.static.get("Eris_thinking_left")}", // Placeholder
                        "Ojalá funcione... ${Icon.static.get("Eris_thinking_left")}" // Placeholder
                    ),
                    ComentaryTraductions(
                        "Não sei o que fazer agora... ${Icon.static.get("Eris_shy_left")}",
                        "I don't know what to do now... ${Icon.static.get("Eris_shy_left")}", // Placeholder
                        "No sé qué hacer ahora... ${Icon.static.get("Eris_shy_left")}" // Placeholder
                    ),
                    ComentaryTraductions(
                        "Jogando com cuidado... ${Icon.static.get("Eris_ok")}",
                        "Playing carefully... ${Icon.static.get("Eris_ok")}", // Placeholder
                        "Jugando con cuidado... ${Icon.static.get("Eris_ok")}" // Placeholder
                    ),
                    ComentaryTraductions(
                        "Essas cartas me deixam nervosa!",
                        "These cards make me nervous!", // Placeholder
                        "¡Estas cartas me ponen nerviosa!" // Placeholder
                    )
                ),
                MoodType.INSECURE to listOf(
                    ComentaryTraductions(
                        "${Icon.static.get("Eris_shy")} Tô com medo de perder...",
                        "${Icon.static.get("Eris_shy")} I'm scared of losing...", // Placeholder
                        "${Icon.static.get("Eris_shy")} Tengo miedo de perder..." // Placeholder
                    ),
                    ComentaryTraductions(
                        "Isso não tá indo bem... ${Icon.static.get("Eris_cry")}",
                        "This is not going well... ${Icon.static.get("Eris_cry")}", // Placeholder
                        "Esto no va bien... ${Icon.static.get("Eris_cry")}" // Placeholder
                    ),
                    ComentaryTraductions(
                        "${Icon.static.get("Eris_cry_left")} Ai, vou estourar, né?",
                        "${Icon.static.get("Eris_cry_left")} Oh, I'm going to bust, right?", // Placeholder
                        "${Icon.static.get("Eris_cry_left")} Ay, voy a pasarme, ¿verdad?" // Placeholder
                    ),
                    ComentaryTraductions(
                        "Minhas cartas tão me traindo! ${Icon.static.get("Eris_shy_left")}",
                        "My cards are betraying me! ${Icon.static.get("Eris_shy_left")}", // Placeholder
                        "¡Mis cartas me están traicionando! ${Icon.static.get("Eris_shy_left")}" // Placeholder
                    ),
                    ComentaryTraductions(
                        "Não gosto nada disso... ${Icon.static.get("Eris_thinking")}",
                        "I don't like this at all... ${Icon.static.get("Eris_thinking")}", // Placeholder
                        "No me gusta nada de esto... ${Icon.static.get("Eris_thinking")}" // Placeholder
                    )
                )
            ),
            BlackjackErisMood.SURPRISED to mapOf(
                MoodType.CONFIDENT to listOf(
                    ComentaryTraductions(
                        "Uou! Minha mão tá incrível! ${Icon.static.get("Eris_enchanted")}",
                        "Wow! My hand is incredible! ${Icon.static.get("Eris_enchanted")}", // Placeholder
                        "¡Wow! ¡Mi mano es increíble! ${Icon.static.get("Eris_enchanted")}" // Placeholder
                    ),
                    ComentaryTraductions(
                        "Caramba, essas cartas são boas?! ${Icon.static.get("Eris_happy_left")}",
                        "Damn, are these cards good?! ${Icon.static.get("Eris_happy_left")}", // Placeholder
                        "¡Caramba, estas cartas son buenas?! ${Icon.static.get("Eris_happy_left")}" // Placeholder
                    ),
                    ComentaryTraductions(
                        "${Icon.static.get("Eris_ok")} Não esperava estar tão bem!",
                        "${Icon.static.get("Eris_ok")} I didn't expect to be doing so well!", // Placeholder
                        "${Icon.static.get("Eris_ok")} ¡No esperaba estar tan bien!" // Placeholder
                    ),
                    ComentaryTraductions(
                        "Olha só essa mão! Tô chocada! ${Icon.static.get("Eris_trusting")}",
                        "Look at this hand! I'm shocked! ${Icon.static.get("Eris_trusting")}", // Placeholder
                        "¡Mira esta mano! ¡Estoy impactada! ${Icon.static.get("Eris_trusting")}" // Placeholder
                    ),
                    ComentaryTraductions(
                        "Quem diria, eu sou boa nisso! ${Icon.static.get("Eris_kiss")}",
                        "Who knew, I'm good at this! ${Icon.static.get("Eris_kiss")}", // Placeholder
                        "¡Quién lo diría, soy buena en esto! ${Icon.static.get("Eris_kiss")}" // Placeholder
                    )
                ),
                MoodType.NEUTRAL to listOf(
                    ComentaryTraductions(
                        "Hmm... interessante. ${Icon.static.get("Eris_thinking")}",
                        "Hmm... interesting. ${Icon.static.get("Eris_thinking")}", // Placeholder
                        "Hmm... interesante. ${Icon.static.get("Eris_thinking")}" // Placeholder
                    ),
                    ComentaryTraductions(
                        "Oh! ${Icon.static.get("Eris_fair")}",
                        "Oh! ${Icon.static.get("Eris_fair")}", // Placeholder
                        "¡Oh! ${Icon.static.get("Eris_fair")}" // Placeholder
                    ),
                    ComentaryTraductions(
                        "Que jogada foi essa? ${Icon.static.get("Eris_ok_left")}",
                        "What kind of move was that? ${Icon.static.get("Eris_ok_left")}", // Placeholder
                        "¿Qué jugada fue esa? ${Icon.static.get("Eris_ok_left")}" // Placeholder
                    ),
                    ComentaryTraductions(
                        "Tá ficando quente essa partida!",
                        "This match is getting hot!", // Placeholder
                        "¡Esta partida se está poniendo caliente!" // Placeholder
                    ),
                    ComentaryTraductions(
                        "Não esperava por isso... ${Icon.static.get("Eris_thinking_left")}",
                        "I didn't expect this... ${Icon.static.get("Eris_thinking_left")}", // Placeholder
                        "No esperaba esto... ${Icon.static.get("Eris_thinking_left")}" // Placeholder
                    )
                ),
                MoodType.INSECURE to listOf(
                    ComentaryTraductions(
                        "O quê?! Minhas cartas são essas?! ${Icon.static.get("Eris_shy")}",
                        "What?! These are my cards?! ${Icon.static.get("Eris_shy")}", // Placeholder
                        "¡¿Qué?! ¡¿Estas son mis cartas?! ${Icon.static.get("Eris_shy")}" // Placeholder
                    ),
                    ComentaryTraductions(
                        "Não entendi, mas... ok. ${Icon.static.get("Eris_cry")}",
                        "I don't understand, but... okay. ${Icon.static.get("Eris_cry")}", // Placeholder
                        "No entendí, pero... está bien. ${Icon.static.get("Eris_cry")}" // Placeholder
                    ),
                    ComentaryTraductions(
                        "${Icon.static.get("Eris_shy_left")} Isso não era pra acontecer...",
                        "${Icon.static.get("Eris_shy_left")} This wasn't supposed to happen...", // Placeholder
                        "${Icon.static.get("Eris_shy_left")} Esto no debería haber pasado..." // Placeholder
                    ),
                    ComentaryTraductions(
                        "Minhas cartas tão zoando comigo! ${Icon.static.get("Eris_cry_left")}",
                        "My cards are messing with me! ${Icon.static.get("Eris_cry_left")}", // Placeholder
                        "¡Mis cartas se están burlando de mí! ${Icon.static.get("Eris_cry_left")}" // Placeholder
                    ),
                    ComentaryTraductions(
                        "Ué, e agora? ${Icon.static.get("Eris_thinking")}",
                        "Uh, now what? ${Icon.static.get("Eris_thinking")}", // Placeholder
                        "¿Y ahora qué? ${Icon.static.get("Eris_thinking")}" // Placeholder
                    )
                )
            ),
            BlackjackErisMood.CONFUSED to mapOf(
                MoodType.CONFIDENT to listOf(
                    ComentaryTraductions(
                        "Acho que minha mão é boa, né? ${Icon.static.get("Eris_trusting")}",
                        "I think my hand is good, right? ${Icon.static.get("Eris_trusting")}", // Placeholder
                        "Creo que mi mano es buena, ¿verdad? ${Icon.static.get("Eris_trusting")}" // Placeholder
                    ),
                    ComentaryTraductions(
                        "Tô ganhando... acho! ${Icon.static.get("Eris_ok")}",
                        "I'm winning... I think! ${Icon.static.get("Eris_ok")}", // Placeholder
                        "Estoy ganando... ¡creo! ${Icon.static.get("Eris_ok")}" // Placeholder
                    ),
                    ComentaryTraductions(
                        "${Icon.static.get("Eris_happy")} Será que sou um gênio?",
                        "${Icon.static.get("Eris_happy")} Am I a genius?", // Placeholder
                        "${Icon.static.get("Eris_happy")} ¿Seré un genio?" // Placeholder
                    ),
                    ComentaryTraductions(
                        "Essas cartas tão a meu favor... acho! ${Icon.static.get("Eris_fair_left")}",
                        "These cards are in my favor... I think! ${Icon.static.get("Eris_fair_left")}", // Placeholder
                        "Estas cartas están a mi favor... ¡creo! ${Icon.static.get("Eris_fair_left")}" // Placeholder
                    ),
                    ComentaryTraductions(
                        "Tô confusa, mas tô na frente! ${Icon.static.get("Eris_kiss")}",
                        "I'm confused, but I'm ahead! ${Icon.static.get("Eris_kiss")}", // Placeholder
                        "¡Estoy confundida, pero estoy a la cabeza! ${Icon.static.get("Eris_kiss")}" // Placeholder
                    )
                ),
                MoodType.NEUTRAL to listOf(
                    ComentaryTraductions(
                        "O que tá acontecendo mesmo? ${Icon.static.get("Eris_thinking")}",
                        "What is actually happening? ${Icon.static.get("Eris_thinking")}", // Placeholder
                        "¿Qué está pasando realmente? ${Icon.static.get("Eris_thinking")}" // Placeholder
                    ),
                    ComentaryTraductions(
                        "... era minha vez? ${Icon.static.get("Eris_shy")}",
                        "... was it my turn? ${Icon.static.get("Eris_shy")}", // Placeholder
                        "... ¿era mi turno? ${Icon.static.get("Eris_shy")}" // Placeholder
                    ),
                    ComentaryTraductions(
                        "Tô meio perdida, mas sigo jogando. ${Icon.static.get("Eris_ok")}",
                        "I'm a bit lost, but I keep playing. ${Icon.static.get("Eris_ok")}", // Placeholder
                        "Estoy un poco perdida, pero sigo jugando. ${Icon.static.get("Eris_ok")}" // Placeholder
                    ),
                    ComentaryTraductions(
                        "Essas cartas tão estranhas... ${Icon.static.get("Eris_thinking_left")}",
                        "These cards are strange... ${Icon.static.get("Eris_thinking_left")}", // Placeholder
                        "Estas cartas son extrañas... ${Icon.static.get("Eris_thinking_left")}" // Placeholder
                    ),
                    ComentaryTraductions(
                        "Alguém explica esse jogo? ${Icon.static.get("Eris_fair")}",
                        "Someone explain this game? ${Icon.static.get("Eris_fair")}", // Placeholder
                        "¿Alguien explica este juego? ${Icon.static.get("Eris_fair")}" // Placeholder
                    )
                ),
                MoodType.INSECURE to listOf(
                    ComentaryTraductions(
                        "Isso não faz sentido. ${Icon.static.get("Eris_cry")}",
                        "This doesn't make sense. ${Icon.static.get("Eris_cry")}", // Placeholder
                        "Esto no tiene sentido. ${Icon.static.get("Eris_cry")}" // Placeholder
                    ),
                    ComentaryTraductions(
                        "${Icon.static.get("Eris_cry_left")} Acho que fiz besteira.",
                        "${Icon.static.get("Eris_cry_left")} I think I messed up.", // Placeholder
                        "${Icon.static.get("Eris_cry_left")} Creo que cometí un error." // Placeholder
                    ),
                    ComentaryTraductions(
                        "Minha mão tá uma bagunça! ${Icon.static.get("Eris_shy_left")}",
                        "My hand is a mess! ${Icon.static.get("Eris_shy_left")}", // Placeholder
                        "¡Mi mano es un desastre! ${Icon.static.get("Eris_shy_left")}" // Placeholder
                    ),
                    ComentaryTraductions(
                        "Por que essas cartas, hein? ${Icon.static.get("Eris_thinking")}",
                        "Why these cards, huh? ${Icon.static.get("Eris_thinking")}", // Placeholder
                        "¿Por qué estas cartas, eh? ${Icon.static.get("Eris_thinking")}" // Placeholder
                    ),
                    ComentaryTraductions(
                        "Tô totalmente confusa agora... ${Icon.static.get("Eris_cry")}",
                        "I'm totally confused now... ${Icon.static.get("Eris_cry")}", // Placeholder
                        "Estoy totalmente confundida ahora... ${Icon.static.get("Eris_cry")}" // Placeholder
                    )
                )
            )
        )

        val group = messages[game.erisMood]!![when (sentimento) {
            "confiante" -> MoodType.CONFIDENT;
            "neutral" -> MoodType.NEUTRAL;
            "inseguro" -> MoodType.INSECURE;
            else -> MoodType.CONFIDENT
        }]!!

        return group.random()
    }
}

private enum class MoodType {
    CONFIDENT,
    INSECURE,
    NEUTRAL
}
