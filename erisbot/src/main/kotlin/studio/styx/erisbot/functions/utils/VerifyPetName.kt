package studio.styx.erisbot.functions.utils

val blacklistNames = listOf(
    "eris", "éris", "porra", "desgraça", "fdp", "puta", "puto", "merda", "merdinha",
    "caralho", "nazista", "nazismo", "narcizista", "desgraçado", "putinha", "diabo",
    "deus", "jesus", "demonio", "demônio", "deusa", "foda", "bosta", "cabrao", "cabra",
    "bicha", "viado", "bucha", "cacete", "filho da puta", "piranha", "idiota", "burro",
    "imbecil", "maldito", "diabinho", "inferno", "putaqueopariu", "@everyone", "@here",
    "god", "hitler", "stalin", "trump", "bolsonaro", "boso", "lula"
)

fun verifyPetName(name: String): List<String> {
    val errors = mutableListOf<String>()

    if (name.contains("@")) {
        errors.add("O nome contém menções proibidas")
    }

    val sanitized = name
        .replace(Regex("/[\\u0300-\\u036f]/g"), "")
        .replace(Regex("/[^a-zA-Z\\s]/g"), "")
        .lowercase()
        .trim()

    blacklistNames.forEach {
        if (sanitized.contains(it)) {
            errors.add("O nome contém a palavra proibida: $it")
        }
    }

    if (sanitized.length < 2) errors.add("O nome deve ter pelo menos 2 caracteres")
    if (sanitized.length > 20) errors.add("O nome deve ter no máximo 20 caracteres")

    return errors
}