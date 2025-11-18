package shared.utils

import io.github.cdimascio.dotenv.Dotenv
import java.lang.NumberFormatException

object Env {
    private val env: Dotenv? = try {
        Dotenv.load()
    } catch (e: Exception) {
        null
    }

    @JvmStatic
    fun get(key: String): SubMethodsEnv? {
        val value = env?.get(key)
        return if (value != null) SubMethodsEnv(value) else null
    }

    @JvmStatic
    fun get(key: String, default: String): String {
        return env?.get(key) ?: default
    }

    @JvmStatic
    fun get(key: String, default: Int): Int {
        return env?.get(key)?.toIntOrNull() ?: default
    }

    @JvmStatic
    fun get(key: String, default: Long): Long {
        return env?.get(key)?.toLongOrNull() ?: default
    }

    @JvmStatic
    fun get(key: String, default: Boolean): Boolean {
        return env?.get(key)?.toBoolean() ?: default
    }
}

class SubMethodsEnv(private val value: String) {
    fun getAsInt(): Int? {
        return value.toIntOrNull()
    }

    fun getAsLong(): Long? {
        return value.toLongOrNull()
    }

    fun getAsDouble(): Double? {
        return value.toDoubleOrNull()
    }

    override fun toString(): String = value
}