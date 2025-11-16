package translates

import translates.commands.economy.general.BalanceTranslate
import translates.commands.economy.general.BalanceTranslateInterface
import translates.commands.economy.general.DailyTranslate
import translates.commands.economy.general.DailyTranslateInterface
import translates.commands.economy.general.TransferTranslate
import translates.commands.economy.general.TransferTranslateInterface

object TranslatesObjects {
    @JvmStatic
    fun getBalancePtBr() = BalanceTranslate.ptbr()

    @JvmStatic
    fun getBalanceEnUs() = BalanceTranslate.enus()

    @JvmStatic
    fun getBalanceEsEs() = BalanceTranslate.eses()

    @JvmStatic
    fun getBalance(locale: String = "enus"): BalanceTranslateInterface {
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

    @JvmStatic

    fun getDaily(locale: String = "enus"): DailyTranslateInterface {
        val transformed = LanguageUtils.transform(locale)

        val result = when (transformed) {
            "ptbr" -> {
                DailyTranslate.ptbr()
            }
            "eses" -> {
                DailyTranslate.eses()
            }
            else -> {
                DailyTranslate.enus()
            }
        }

        return result
    }

    @JvmStatic
    fun getTransferCommand(locale: String = "enus"): TransferTranslateInterface {
        val transformed = LanguageUtils.transform(locale)

        val result = when (transformed) {
            "ptbr" -> {
                TransferTranslate.ptbr()
            }
            "eses" -> {
                TransferTranslate.eses()
            }
            else -> {
                TransferTranslate.enus()
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