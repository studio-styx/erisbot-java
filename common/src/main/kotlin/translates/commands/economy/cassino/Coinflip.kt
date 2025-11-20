package translates.commands.economy.cassino

import shared.utils.Icon

enum class CoinflipSide {
    HEADS,
    TAILS
}

interface CoinflipCommandInterface {
    fun notEnoughMoney(): String
    fun won(side: CoinflipSide, reward: Double): String
    fun lose(side: CoinflipSide, amountApposted: Double): String
    fun logWon(side: CoinflipSide, reward: Double): String
    fun logLose(side: CoinflipSide, amountApposted: Double): String
}

class CoinflipCommandTranslate {
    companion object {
        @JvmStatic
        fun ptbr() = PtBrCoinflip()

        @JvmStatic
        fun enus() = EnUsCoinflip()

        @JvmStatic
        fun eses() = EsEsCoinflip()
    }
}

class PtBrCoinflip : CoinflipCommandInterface {
    override fun notEnoughMoney(): String {
        return "${Icon.static.get("denied")} | Você não tem dinheiro suficiente para apostar!"
    }

    override fun won(side: CoinflipSide, reward: Double): String {
        return "${Icon.static.get("Eris_happy")} | Parabéns! Você ganhou **$reward** stx! A moeda caiu em **${if (side == CoinflipSide.HEADS) "cara" else "coroa"}**."
    }

    override fun lose(side: CoinflipSide, amountApposted: Double): String {
        return "${Icon.static.get("Eris_cry")} | Que pena! Você perdeu **$amountApposted** stx! A moeda caiu em **${if (side == CoinflipSide.HEADS) "cara" else "coroa"}**."
    }

    override fun logWon(side: CoinflipSide, reward: Double): String {
        return "Apostou em ${side.toString()} e ganhou **$reward** stx"
    }

    override fun logLose(side: CoinflipSide, amountApposted: Double): String {
       return "Apostou em ${side.toString()} e perdeu **$amountApposted** stx"
    }
}

class EnUsCoinflip : CoinflipCommandInterface {
    override fun notEnoughMoney(): String {
        return "${Icon.static.get("denied")} | You don't have enough money to bet!"
    }

    override fun won(side: CoinflipSide, reward: Double): String {
        return "${Icon.static.get("Eris_happy")} | Congratulations! You won **$reward** stx! The coin landed on **${if (side == CoinflipSide.HEADS) "heads" else "tails"}.**"
    }

    override fun lose(side: CoinflipSide, amountApposted: Double): String {
        return "${Icon.static.get("Eris_cry")} | Too bad! You lost **$amountApposted** stx! The coin landed on **${if (side == CoinflipSide.HEADS) "heads" else "tails"}**."
    }

    override fun logWon(side: CoinflipSide, reward: Double): String {
        return "Bet on ${side.toString()} and won **$reward** stx"
    }

    override fun logLose(side: CoinflipSide, amountApposted: Double): String {
        return "Bet on ${side.toString()} and lost **$amountApposted** stx"
    }
}

class EsEsCoinflip : CoinflipCommandInterface {
    override fun notEnoughMoney(): String {
        return "${Icon.static.get("denied")} | ¡No tienes suficiente dinero para apostar!"
    }

    override fun won(side: CoinflipSide, reward: Double): String {
        return "${Icon.static.get("Eris_happy")} | ¡Felicidades! ¡Ganaste **$reward** stx! La moneda cayó en **${if (side == CoinflipSide.HEADS) "cara" else "cruz"}**."
    }

    override fun lose(side: CoinflipSide, amountApposted: Double): String {
        return "${Icon.static.get("Eris_cry")} | ¡Qué pena! ¡Perdiste **$amountApposted** stx! La moneda cayó en **${if (side == CoinflipSide.HEADS) "cara" else "cruz"}**."
    }

    override fun logWon(side: CoinflipSide, reward: Double): String {
        return "Apostó en ${side.toString()} y ganó **$reward** stx"
    }

    override fun logLose(side: CoinflipSide, amountApposted: Double): String {
        return "Apostó en ${side.toString()} y perdió **$amountApposted** stx"
    }
}