package translates

import translates.commands.economy.general.BalanceTranslate
import translates.commands.economy.general.BalanceTranslateInterface

object TranslatesObjects {
    @JvmStatic
    fun getBalancePtBr() = BalanceTranslate.ptbr()

    @JvmStatic
    fun getBalanceEnUs() = BalanceTranslate.enus()

    @JvmStatic
    fun getBalanceEsEs() = BalanceTranslate.eses()

    @JvmStatic
    fun getBalance(locale: String = "enus"): BalanceTranslateInterface {
        println("=== DEBUG TRANSLATES ===")
        val transformed = LanguageUtils.transform(locale)

        val result = when (transformed) {
            "ptbr" -> {
                BalanceTranslate.ptbr()
            }
            "eses" -> {
                BalanceTranslate.eses()
            }
            else -> {
                BalanceTranslate.enus()
            }
        }
        return result
    }
}

object LanguageUtils {
    @JvmStatic
    fun transform(locale: String): String {
        val lower = locale.lowercase()

        return when (lower) {
            "pt-br", "pt_br", "portuguese", "portuguese_brazilian", "ptbr" -> {
                "ptbr"
            }
            "es-es", "es_es", "es-419", "es_419", "spanish", "eses",
            "es-mx", "es_mx", "es-ar", "es_ar", "es-cl", "es_cl",
            "es-co", "es_co", "es-pe", "es_pe" -> {
                "eses"
            }
            "en-us", "en_us", "english", "enus" -> {
                "enus"
            }
            else -> {
                "enus"
            }
        }
    }
}