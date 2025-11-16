package shared.utils

data class Grammar(
    val name: String,
    val gender: GenderUnknown,
) {
    val article: String
        get() = if (gender == GenderUnknown.MALE || gender == GenderUnknown.UNKNOWN) "o" else "a"

    val pronoun: String
        get() = if (gender == GenderUnknown.MALE || gender == GenderUnknown.UNKNOWN) "ele" else "ela"

    val pronounStrong: String
        get() = if (gender == GenderUnknown.MALE || gender == GenderUnknown.UNKNOWN) "nele" else "nela"

    fun adj(masc: String, fem: String) =
        if (gender == GenderUnknown.MALE || gender == GenderUnknown.UNKNOWN) masc else fem

    val dePronounStrong: String
        get() = if (gender == GenderUnknown.MALE || gender == GenderUnknown.UNKNOWN) "dele" else "dela"

    val dePronoun: String
        get() = if (gender == GenderUnknown.MALE || gender == GenderUnknown.UNKNOWN) "do" else "da"
}

open class OpenGrammar(
    val name: String,
    val gender: GenderUnknown,
) {
    val article: String
        get() = if (gender == GenderUnknown.MALE || gender == GenderUnknown.UNKNOWN) "o" else "a"

    val pronoun: String
        get() = if (gender == GenderUnknown.MALE || gender == GenderUnknown.UNKNOWN) "ele" else "ela"

    val pronounStrong: String
        get() = if (gender == GenderUnknown.MALE || gender == GenderUnknown.UNKNOWN) "nele" else "nela"

    fun adj(masc: String, fem: String) =
        if (gender == GenderUnknown.MALE || gender == GenderUnknown.UNKNOWN) masc else fem

    val dePronounStrong: String
        get() = if (gender == GenderUnknown.MALE || gender == GenderUnknown.UNKNOWN) "dele" else "dela"

    val dePronoun: String
        get() = if (gender == GenderUnknown.MALE || gender == GenderUnknown.UNKNOWN) "do" else "da"
}

// English Grammar
data class EnglishGrammar(
    val name: String,
    val gender: GenderUnknown,
) {
    val article: String
        get() = if (gender == GenderUnknown.MALE || gender == GenderUnknown.UNKNOWN) "the" else "the"

    val pronoun: String
        get() = when {
            gender == GenderUnknown.MALE -> "he"
            gender == GenderUnknown.FEMALE -> "she"
            else -> "they"
        }

    val pronounObject: String
        get() = when {
            gender == GenderUnknown.MALE -> "him"
            gender == GenderUnknown.FEMALE -> "her"
            else -> "them"
        }

    val possessive: String
        get() = when {
            gender == GenderUnknown.MALE -> "his"
            gender == GenderUnknown.FEMALE -> "her"
            else -> "their"
        }

    val possessiveAbsolute: String
        get() = when {
            gender == GenderUnknown.MALE -> "his"
            gender == GenderUnknown.FEMALE -> "hers"
            else -> "theirs"
        }

    val reflexive: String
        get() = when {
            gender == GenderUnknown.MALE -> "himself"
            gender == GenderUnknown.FEMALE -> "herself"
            else -> "themselves"
        }

    fun adj(masc: String, fem: String) =
        if (gender == GenderUnknown.MALE || gender == GenderUnknown.UNKNOWN) masc else fem

    val userPronoun: String
        get() = when {
            gender == GenderUnknown.MALE -> "male user"
            gender == GenderUnknown.FEMALE -> "female user"
            else -> "user"
        }
}

open class OpenEnglishGrammar(
    val name: String,
    val gender: GenderUnknown,
) {
    val article: String
        get() = if (gender == GenderUnknown.MALE || gender == GenderUnknown.UNKNOWN) "the" else "the"

    val pronoun: String
        get() = when {
            gender == GenderUnknown.MALE -> "he"
            gender == GenderUnknown.FEMALE -> "she"
            else -> "they"
        }

    val pronounObject: String
        get() = when {
            gender == GenderUnknown.MALE -> "him"
            gender == GenderUnknown.FEMALE -> "her"
            else -> "them"
        }

    val possessive: String
        get() = when {
            gender == GenderUnknown.MALE -> "his"
            gender == GenderUnknown.FEMALE -> "her"
            else -> "their"
        }

    val possessiveAbsolute: String
        get() = when {
            gender == GenderUnknown.MALE -> "his"
            gender == GenderUnknown.FEMALE -> "hers"
            else -> "theirs"
        }

    val reflexive: String
        get() = when {
            gender == GenderUnknown.MALE -> "himself"
            gender == GenderUnknown.FEMALE -> "herself"
            else -> "themselves"
        }

    fun adj(masc: String, fem: String) =
        if (gender == GenderUnknown.MALE || gender == GenderUnknown.UNKNOWN) masc else fem

    open val userPronoun: String
        get() = when {
            gender == GenderUnknown.MALE -> "male user"
            gender == GenderUnknown.FEMALE -> "female user"
            else -> "user"
        }
}

// Spanish Grammar
data class SpanishGrammar(
    val name: String,
    val gender: GenderUnknown,
) {
    val article: String
        get() = if (gender == GenderUnknown.MALE || gender == GenderUnknown.UNKNOWN) "el" else "la"

    val pronoun: String
        get() = if (gender == GenderUnknown.MALE || gender == GenderUnknown.UNKNOWN) "él" else "ella"

    val pronounObject: String
        get() = if (gender == GenderUnknown.MALE || gender == GenderUnknown.UNKNOWN) "lo" else "la"

    val prepositionalPronoun: String
        get() = if (gender == GenderUnknown.MALE || gender == GenderUnknown.UNKNOWN) "él" else "ella"

    val possessive: String
        get() = if (gender == GenderUnknown.MALE || gender == GenderUnknown.UNKNOWN) "su" else "su"

    val possessiveAbsolute: String
        get() = if (gender == GenderUnknown.MALE || gender == GenderUnknown.UNKNOWN) "suyo" else "suya"

    val reflexive: String
        get() = if (gender == GenderUnknown.MALE || gender == GenderUnknown.UNKNOWN) "sí mismo" else "sí misma"

    fun adj(masc: String, fem: String) =
        if (gender == GenderUnknown.MALE || gender == GenderUnknown.UNKNOWN) masc else fem

    val dePronoun: String
        get() = if (gender == GenderUnknown.MALE || gender == GenderUnknown.UNKNOWN) "del" else "de la"
}

open class OpenSpanishGrammar(
    val name: String,
    val gender: GenderUnknown,
) {
    val article: String
        get() = if (gender == GenderUnknown.MALE || gender == GenderUnknown.UNKNOWN) "el" else "la"

    val pronoun: String
        get() = if (gender == GenderUnknown.MALE || gender == GenderUnknown.UNKNOWN) "él" else "ella"

    val pronounObject: String
        get() = if (gender == GenderUnknown.MALE || gender == GenderUnknown.UNKNOWN) "lo" else "la"

    val prepositionalPronoun: String
        get() = if (gender == GenderUnknown.MALE || gender == GenderUnknown.UNKNOWN) "él" else "ella"

    val possessive: String
        get() = if (gender == GenderUnknown.MALE || gender == GenderUnknown.UNKNOWN) "su" else "su"

    val possessiveAbsolute: String
        get() = if (gender == GenderUnknown.MALE || gender == GenderUnknown.UNKNOWN) "suyo" else "suya"

    val reflexive: String
        get() = if (gender == GenderUnknown.MALE || gender == GenderUnknown.UNKNOWN) "sí mismo" else "sí misma"

    fun adj(masc: String, fem: String) =
        if (gender == GenderUnknown.MALE || gender == GenderUnknown.UNKNOWN) masc else fem

    val dePronoun: String
        get() = if (gender == GenderUnknown.MALE || gender == GenderUnknown.UNKNOWN) "del" else "de la"
}