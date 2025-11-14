package translates.commands.economy.general

enum class Gender {
    MASC, FEM
}

data class PetGrammar(
    val name: String,
    val gender: Gender
) {
    val article: String
        get() = if (gender == Gender.MASC) "o" else "a"

    val pronoun: String
        get() = if (gender == Gender.MASC) "ele" else "ela"

    val pronounStrong: String
        get() = if (gender == Gender.MASC) "nele" else "nela"

    fun adj(masc: String, fem: String) =
        if (gender == Gender.MASC) masc else fem

    val dePronoun: String
        get() = if (gender == Gender.MASC) "dele" else "dela"
}


interface DailyTranslateInterface {
    fun manyAttempts(attempts: Int): String
    fun cooldown(expiresAt: Long): String
    fun message(reward: Int): String
    fun message(reward: Int, petName: String, petGender: Gender): String
    fun log(reward: Int): String
    fun log(reward: Int, petName: String, petGender: Gender): String
}

class DailyTranslate {
    companion object {
        @JvmStatic
        fun ptbr()
    }
}

class ptbr : DailyTranslateInterface {
    override fun manyAttempts(attempts: Int): String {

    }
}