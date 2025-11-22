package shared.utils

import java.math.BigDecimal
import java.time.LocalDateTime

object Utils {

    @JvmStatic
    fun getRandomInt(min: Int, max: Int): Int {
        return (Math.random() * (max - min + 1) + min).toInt()
    }

    @JvmStatic
    fun getRandomDouble(min: Double, max: Double): Double {
        return Math.random() * (max - min) + min
    }

    @JvmStatic
    fun formatMoney(value: BigDecimal): String {
        return String.format("%,d", value.toLong()) // 1.234.567
    }

    @JvmStatic
    fun formatNumber(number: Long): String {
        return String.format("%,d", number).replace(",", ".")
    }

    @JvmStatic
    fun formatNumber(number: Double): String {
        return String.format("%,.0f", number).replace(",", ".")
    }

    @JvmStatic
    fun <T> getRandomListValue(list: List<T>): T {
        return list[getRandomInt(0, list.size - 1)]
    }

    @JvmStatic
    fun formatDiscordTime(time: Long, type: DiscordTimeStyle): String {
        val style = convertDiscordTimeStyleToString(type)
        return "<t:$time:$style>"
    }

    @JvmStatic
    fun formatDiscordTime(time: Long, type: String): String {
        return "<t:$time:$type>"
    }

    @JvmStatic
    fun formatDiscordTime(time: LocalDateTime, type: DiscordTimeStyle): String {
        val style = convertDiscordTimeStyleToString(type)
        return "<t:${time.toEpochSecond(java.time.ZoneOffset.UTC)}:$style>"
    }

    @JvmStatic
    fun formatDiscordTime(time: LocalDateTime, type: String): String {
        return "<t:${time.toEpochSecond(java.time.ZoneOffset.UTC)}:$type>"
    }

    @JvmStatic
    fun convertDiscordTimeStyleToString(style: DiscordTimeStyle): String {
        return when (style) {
            DiscordTimeStyle.RELATIVE -> "R"
            DiscordTimeStyle.LONGDATE -> "D"
            DiscordTimeStyle.LONGDATETIME -> "F"
            DiscordTimeStyle.LONGTIME -> "T"
            DiscordTimeStyle.SHORTDATE -> "d"
            DiscordTimeStyle.SHORTDATETIME -> "f"
            DiscordTimeStyle.SHORTTIME -> "t"
        }
    }

    @JvmStatic
    fun getNameGender(name: String): GenderUnknown {
        val cleanName = name.trim().lowercase()

        // Primeiro verificar plurais
        if (cleanName.endsWith("as")) return GenderUnknown.FEMALE
        if (cleanName.endsWith("os")) return GenderUnknown.MALE

        // Depois verificar singulares
        if (cleanName.endsWith("a")) return GenderUnknown.FEMALE
        if (cleanName.endsWith("o")) return GenderUnknown.MALE

        // Lista de exceções comuns
        val maleExceptions = listOf("josé", "josue", "davi", "rami", "roni")
        val femaleExceptions = listOf("eve", "marie", "rose")

        return when {
            maleExceptions.any { cleanName.endsWith(it) } -> GenderUnknown.MALE
            femaleExceptions.any { cleanName.endsWith(it) } -> GenderUnknown.FEMALE
            else -> GenderUnknown.UNKNOWN
        }
    }

    @JvmStatic
    fun replaceText(text: String, replacements: Map<String, String>): String {
        var result = text
        replacements.forEach { (key, value) ->
            result = result.replace("{$key}", value)
        }

        return result
    }

    @JvmStatic
    fun brBuilder(vararg texts: String?): String {
        val nonNullTexts = texts.filterNotNull()
        return nonNullTexts.joinToString("\n")
    }

    @JvmStatic
    fun brBuilder(vararg texts: String?, replacements: Map<String, String>): String {
        var result = texts.filterNotNull().joinToString("\n")
        replacements.forEach { (key, value) ->
            result = result.replace("{$key}", value)
        }

        return result
    }

    @JvmStatic
    fun <T> alternate(primary: T, default: T): T {
        return primary ?: default!!
    }
}

enum class DiscordTimeStyle {
    RELATIVE,
    LONGDATE,
    LONGDATETIME,
    LONGTIME,
    SHORTDATE,
    SHORTDATETIME,
    SHORTTIME
}

enum class GenderUnknown { MALE, FEMALE, UNKNOWN }