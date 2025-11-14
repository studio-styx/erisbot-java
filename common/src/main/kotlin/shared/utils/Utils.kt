package shared.utils

import java.math.BigDecimal

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
}