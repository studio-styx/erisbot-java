package shared

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object Cache {
    private val cache = ConcurrentHashMap<String, CacheEntry<Any>>()

    private data class CacheEntry<V>(val value: V, val expirationTime: Long?)

    // Método principal SET
    @JvmStatic
    fun set(key: String, value: Any, durationInSeconds: Long) {
        val expirationTime = System.currentTimeMillis() + (durationInSeconds * 1000)
        cache[key] = CacheEntry(value, expirationTime)
    }

    // Overload para usar TimeUnit
    @JvmStatic
    fun set(key: String, value: Any, duration: Long, timeUnit: TimeUnit) {
        val durationInSeconds = timeUnit.toSeconds(duration)
        set(key, value, durationInSeconds)
    }

    @JvmStatic
    fun set(key: String, value: Any) {
        cache[key] = CacheEntry(value, null)
    }

    // Método principal GET
    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String): T? {
        val entry = cache[key] ?: return null

        val expirationTime = entry.expirationTime
        if (expirationTime != null && System.currentTimeMillis() > expirationTime) {
            cache.remove(key)
            return null
        }

        return entry.value as? T
    }

    // Métodos utilitários
    @JvmStatic
    fun contains(key: String): Boolean {
        return get<Any>(key) != null
    }

    @JvmStatic
    fun remove(key: String) {
        cache.remove(key)
    }

    @JvmStatic
    fun clear() {
        cache.clear()
    }

    @JvmStatic
    fun size(): Int {
        return cache.size
    }

    // Método para debug - ver entradas expiradas
    @JvmStatic
    fun cleanExpired() {
        val now = System.currentTimeMillis()
        val iterator = cache.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val cacheEntry = entry.value
            val expirationTime = cacheEntry.expirationTime
            if (expirationTime != null && now > expirationTime) {
                iterator.remove()
            }
        }
    }
}