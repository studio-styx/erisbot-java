package redis

import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import shared.utils.Env
import java.net.URI
import java.util.concurrent.TimeUnit

object RedisManager {
    private val redisUrl = Env.get("REDIS_URL").toString() ?:
        throw IllegalArgumentException("Redis url não encontrada no .env")

    private val poolConfig = JedisPoolConfig().apply {
        maxTotal = 50
        maxIdle = 20
        testOnBorrow = true
        testOnReturn = true
        testWhileIdle = true
    }

    // JedisPool usando URI - FUNCIONA EM TODAS AS VERSÕES
    private val jedisPool: JedisPool = JedisPool(
        poolConfig,
        URI.create(redisUrl),
        20000, // connectionTimeout
        10000  // soTimeout
    )

    // Operações Redis
    @JvmStatic
    suspend fun set(key: String, value: String): String? = withContext(Dispatchers.IO) {
        jedisPool.resource.use { jedis ->
            jedis.set(key, value)
        }
    }

    @JvmStatic
    suspend fun set(key: String, value: String, timeInSeconds: Long): String? = withContext(Dispatchers.IO) {
        jedisPool.resource.use { jedis ->
            jedis.setex(key, timeInSeconds, value)
        }
    }

    @JvmStatic
    suspend fun set(key: String, value: String, time: Long, unit: TimeUnit): String? = withContext(Dispatchers.IO) {
        var timeInSeconds = unit.toSeconds(time)

        jedisPool.resource.use { jedis ->
            jedis.setex(key, timeInSeconds, value)
        }
    }

    @JvmStatic
    suspend fun get(key: String): String? = withContext(Dispatchers.IO) {
        jedisPool.resource.use { jedis ->
            jedis.get(key)
        }
    }

    @JvmStatic
    suspend fun ping(): String = withContext(Dispatchers.IO) {
        jedisPool.resource.use { jedis ->
            jedis.ping()
        }
    }

    @JvmStatic
    suspend fun exists(key: String): Boolean = withContext(Dispatchers.IO) {
        jedisPool.resource.use { jedis ->
            jedis.exists(key)
        }
    }

    @JvmStatic
    suspend fun delete(key: String): Long = withContext(Dispatchers.IO) {
        jedisPool.resource.use { jedis ->
            jedis.del(key)
        }
    }

    @JvmStatic
    fun setBlocking(key: String, value: String): String? = runBlocking {
        set(key, value)
    }

    @JvmStatic
    fun setBlocking(key: String, value: String, timeInSeconds: Long): String? = runBlocking {
        set(key, value, timeInSeconds)
    }

    @JvmStatic
    fun setBlocking(key: String, value: String, time: Long, unit: TimeUnit): String? = runBlocking {
        set(key, value, time, unit)
    }

    @JvmStatic
    fun getBlocking(key: String): String? = runBlocking {
        get(key)
    }

    @JvmStatic
    fun existsBlocking(key: String): Boolean = runBlocking {
        exists(key)
    }

    @JvmStatic
    fun deleteBlocking(key: String): Long = runBlocking {
        delete(key)
    }

    @JvmStatic
    fun shutdown() {
        jedisPool.close()
    }
}