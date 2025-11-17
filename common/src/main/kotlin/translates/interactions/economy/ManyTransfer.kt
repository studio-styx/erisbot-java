package translates.interactions.economy

import shared.utils.Icon

interface ManyTransferInteractionInterface {
    fun userCannotUseThisButton(): String
    fun insufficientFunds(): String
    fun message(usersLength: Int, amount: Double): String
}

class ManyTransferInteraction {
    companion object {
        @JvmStatic
        fun ptbr() = PtBrManyTransferInteraction()

        @JvmStatic
        fun enus() = EnUsManyTransferInteraction()

        @JvmStatic
        fun eses() = EsEsManyTransferInteraction()
    }
}

class PtBrManyTransferInteraction : ManyTransferInteractionInterface {
    override fun userCannotUseThisButton(): String {
        return "${Icon.static.get("denied")} | Você não pode usar esse botão!"
    }

    override fun insufficientFunds(): String {
        return "${Icon.static.get("denied")} | Você não tem dinheiro suficiente para pagar esses usuários!"
    }

    override fun message(usersLength: Int, amount: Double): String {
        return "${Icon.static.get("Eris_happy")} | Sucesso! agora os $usersLength usuários devem aceitar os $amount stx!"
    }
}

// English Version
class EnUsManyTransferInteraction : ManyTransferInteractionInterface {
    override fun userCannotUseThisButton(): String {
        return "${Icon.static.get("denied")} | You cannot use this button!"
    }

    override fun insufficientFunds(): String {
        return "${Icon.static.get("denied")} | You don't have enough money to pay these users!"
    }

    override fun message(usersLength: Int, amount: Double): String {
        return "${Icon.static.get("Eris_happy")} | Success! Now the $usersLength users must accept the $amount stx!"
    }
}

// Spanish Version
class EsEsManyTransferInteraction : ManyTransferInteractionInterface {
    override fun userCannotUseThisButton(): String {
        return "${Icon.static.get("denied")} | ¡No puedes usar este botón!"
    }

    override fun insufficientFunds(): String {
        return "${Icon.static.get("denied")} | ¡No tienes suficiente dinero para pagar a estos usuarios!"
    }

    override fun message(usersLength: Int, amount: Double): String {
        return "${Icon.static.get("Eris_happy")} | ¡Éxito! Ahora los $usersLength usuarios deben aceptar los $amount stx!"
    }
}