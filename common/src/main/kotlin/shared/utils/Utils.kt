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