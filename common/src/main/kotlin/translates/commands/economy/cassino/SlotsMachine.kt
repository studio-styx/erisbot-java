package translates.commands.economy.cassino

import shared.utils.Icon

interface SlotMachineTranslateInterface {
    val notEnoughMoney: String
    fun slot1(slot1: String): String
    fun slot2(slot1: String, slot2: String): String
    fun winMessage(slot1: String, slot2: String, slot3: String, winAmount: Double): String
    fun loseMessage(slot1: String, slot2: String, slot3: String, amount: Double): String
    fun log(isWin: Boolean, winAmount: Double, amount: Double): String
    val title: String
}

class SlotMachineTranslate {
    companion object {
        @JvmStatic
        fun ptbr() = PtBrSlotMachine()

        @JvmStatic
        fun enus() = EnUsSlotMachine()

        @JvmStatic
        fun eses() = EsEsSlotMachine()
    }
}

class PtBrSlotMachine : SlotMachineTranslateInterface {
    override val notEnoughMoney = "${Icon.static.get("denied")} | Você não tem dinheiro suficiente para apostar."

    override val title = "## Caça níqueis"

    override fun slot1(slot1: String): String {
        return "$slot1 | - | - \n\nGirando..."
    }

    override fun slot2(slot1: String, slot2: String): String {
        return "$slot1 | $slot2 | - \n\nGirando..."
    }

    override fun winMessage(slot1: String, slot2: String, slot3: String, winAmount: Double): String {
        return "$slot1 | $slot2 | $slot3\n\n${Icon.static.get("success")} **JACKPOT!** Você ganhou **$winAmount** STX!"
    }

    override fun loseMessage(slot1: String, slot2: String, slot3: String, amount: Double): String {
        return "$slot1 | $slot2 | $slot3\n\nVocê perdeu **$amount** STX."
    }

    override fun log(isWin: Boolean, winAmount: Double, amount: Double): String {
        return "Apostou no caça-níqueis e ${if (isWin) "ganhou $winAmount stx" else "perdeu $amount stx"}"
    }
}

class EnUsSlotMachine : SlotMachineTranslateInterface {
    override val notEnoughMoney = "${Icon.static.get("denied")} | You don't have enough money to bet."

    override val title = "## Slot Machine"

    override fun slot1(slot1: String): String {
        return "$slot1 | - | - \n\nSpinning..."
    }

    override fun slot2(slot1: String, slot2: String): String {
        return "$slot1 | $slot2 | - \n\nSpinning..."
    }

    override fun winMessage(slot1: String, slot2: String, slot3: String, winAmount: Double): String {
        return "$slot1 | $slot2 | $slot3\n\n${Icon.static.get("success")} **JACKPOT!** You won **$winAmount** STX!"
    }

    override fun loseMessage(slot1: String, slot2: String, slot3: String, amount: Double): String {
        return "$slot1 | $slot2 | $slot3\n\nYou lost **$amount** STX."
    }

    override fun log(isWin: Boolean, winAmount: Double, amount: Double): String {
        return "Bet on slots and ${if (isWin) "won $winAmount stx" else "lost $amount stx"}"
    }
}

class EsEsSlotMachine : SlotMachineTranslateInterface {
    override val notEnoughMoney = "${Icon.static.get("denied")} | No tienes suficiente dinero para apostar."

    override val title = "## Tragaperras"

    override fun slot1(slot1: String): String {
        return "$slot1 | - | - \n\nGirando..."
    }

    override fun slot2(slot1: String, slot2: String): String {
        return "$slot1 | $slot2 | - \n\nGirando..."
    }

    override fun winMessage(slot1: String, slot2: String, slot3: String, winAmount: Double): String {
        return "$slot1 | $slot2 | $slot3\n\n${Icon.static.get("success")} **¡JACKPOT!** ¡Ganaste **$winAmount** STX!"
    }

    override fun loseMessage(slot1: String, slot2: String, slot3: String, amount: Double): String {
        return "$slot1 | $slot2 | $slot3\n\nPerdiste **$amount** STX."
    }

    override fun log(isWin: Boolean, winAmount: Double, amount: Double): String {
        return "Apostó en la tragaperras y ${if (isWin) "ganó $winAmount stx" else "perdió $amount stx"}"
    }
}