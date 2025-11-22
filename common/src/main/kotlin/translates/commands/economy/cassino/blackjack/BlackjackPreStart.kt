package translates.commands.economy.cassino.blackjack

import shared.utils.Utils

interface BlackjackPreStartInterface {
    fun notEnoughMoney(): String
    fun title(games: Int): String
    fun classicDealer(): String
    fun erisEasy(): String
    fun erisNormal(): String
    fun erisHard(): String
    fun erisNightmare(): String
    fun otherPlayer(): String
}

class BlackjackPreStart {
    companion object {
        @JvmStatic
        fun ptbr() = PtBrBlackjackPreStart()

        @JvmStatic
        fun enus() = EnUsBlackjackPreStart()

        @JvmStatic
        fun eses() = EsEsBlackjackPreStart()
    }
}

class PtBrBlackjackPreStart : BlackjackPreStartInterface {
    override fun notEnoughMoney(): String {
        return "Você não tem dinheiro suficiente para jogar Blackjack."
    }

    override fun title(games: Int): String {
        return Utils.brBuilder(
            "# BlackJack",
            "Escolha um modo de jogo",
            if (games >= 4) "> Alguns modos estão desativados pois você está jogando muito." else null
        )
    }

    override fun classicDealer(): String {
        return "Dealer Clássico"
    }

    override fun erisEasy(): String {
        return "Eris (Fácil)"
    }

    override fun erisNormal(): String {
        return "Eris (Normal)"
    }

    override fun erisHard(): String {
        return "Eris (Difícil)"
    }

    override fun erisNightmare(): String {
        return "Eris (Pesadelo)"
    }

    override fun otherPlayer(): String {
        return "Jogar contra outro jogador"
    }
}

class EnUsBlackjackPreStart : BlackjackPreStartInterface {
    override fun notEnoughMoney(): String {
        return "You don't have enough money to play Blackjack."
    }

    override fun title(games: Int): String {
        return Utils.brBuilder(
            "# BlackJack",
            "Choose a game mode",
            if (games >= 4) "> Some modes are disabled because you are playing too much." else null
        )
    }

    override fun classicDealer(): String {
        return "Classic Dealer"
    }

    override fun erisEasy(): String {
        return "Eris (Easy)"
    }

    override fun erisNormal(): String {
        return "Eris (Normal)"
    }

    override fun erisHard(): String {
        return "Eris (Hard)"
    }

    override fun erisNightmare(): String {
        return "Eris (Nightmare)"
    }

    override fun otherPlayer(): String {
        return "Play against another player"
    }
}

class EsEsBlackjackPreStart : BlackjackPreStartInterface {
    override fun notEnoughMoney(): String {
        return "No tienes suficiente dinero para jugar Blackjack."
    }

    override fun title(games: Int): String {
        return Utils.brBuilder(
            "# BlackJack",
            "Elige un modo de juego",
            if (games >= 4) "> Algunos modos están deshabilitados porque estás jugando demasiado." else null
        )
    }

    override fun classicDealer(): String {
        return "Crupier Clásico"
    }

    override fun erisEasy(): String {
        return "Eris (Fácil)"
    }

    override fun erisNormal(): String {
        return "Eris (Normal)"
    }

    override fun erisHard(): String {
        return "Eris (Difícil)"
    }

    override fun erisNightmare(): String {
        return "Eris (Pesadilla)"
    }

    override fun otherPlayer(): String {
        return "Jugar contra otro jugador"
    }
}