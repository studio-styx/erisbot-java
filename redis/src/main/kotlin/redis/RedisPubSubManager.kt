package redis

import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPubSub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.net.URI
import java.util.concurrent.Executors

object RedisPubSubManager {
    private val redisUrl = System.getenv("REDIS_URL") ?:
        throw IllegalArgumentException("Redis url não encontrada no .env")

    // Pool separado para Pub/Sub (subscribe é bloqueante)
    private val jedisPool: JedisPool = JedisPool(URI.create(redisUrl))

    // Executor para rodar subscribers em background
    private val subscriberExecutor = Executors.newFixedThreadPool(4)

    // PUBLISH - Similar às outras operações
    suspend fun publish(channel: String, message: String): Long = withContext(Dispatchers.IO) {
        jedisPool.resource.use { jedis ->
            jedis.publish(channel, message)
        }
    }

    // SUBSCRIBE - Usando Flow do Kotlin
    fun subscribe(vararg channels: String): Flow<PubSubMessage> = callbackFlow {
        val jedis = jedisPool.resource
        val pubSub = object : JedisPubSub() {
            override fun onMessage(channel: String, message: String) {
                trySend(PubSubMessage(channel, message, MessageType.MESSAGE))
            }

            override fun onSubscribe(channel: String, subscribedChannels: Int) {
                trySend(PubSubMessage(channel, "Subscribed to $channel", MessageType.SUBSCRIBE))
            }

            override fun onUnsubscribe(channel: String, subscribedChannels: Int) {
                trySend(PubSubMessage(channel, "Unsubscribed from $channel", MessageType.UNSUBSCRIBE))
            }

            override fun onPMessage(pattern: String, channel: String, message: String) {
                trySend(PubSubMessage(channel, message, MessageType.PMESSAGE, pattern))
            }
        }

        // Executa o subscribe em uma thread separada (é bloqueante)
        subscriberExecutor.submit {
            try {
                jedis.subscribe(pubSub, *channels)
            } catch (e: Exception) {
                close(e)
            } finally {
                jedis.close()
            }
        }

        awaitClose {
            pubSub.unsubscribe()
            subscriberExecutor.shutdownNow()
        }
    }

    // PSUBSCRIBE - Para pattern matching
    fun psubscribe(vararg patterns: String): Flow<PubSubMessage> = callbackFlow {
        val jedis = jedisPool.resource
        val pubSub = object : JedisPubSub() {
            override fun onPMessage(pattern: String, channel: String, message: String) {
                trySend(PubSubMessage(channel, message, MessageType.PMESSAGE, pattern))
            }

            override fun onPSubscribe(pattern: String, subscribedChannels: Int) {
                trySend(PubSubMessage(pattern, "PSubscribed to $pattern", MessageType.PSUBSCRIBE))
            }

            override fun onPUnsubscribe(pattern: String, subscribedChannels: Int) {
                trySend(PubSubMessage(pattern, "PUnsubscribed from $pattern", MessageType.PUNSUBSCRIBE))
            }
        }

        subscriberExecutor.submit {
            try {
                jedis.psubscribe(pubSub, *patterns)
            } catch (e: Exception) {
                close(e)
            } finally {
                jedis.close()
            }
        }

        awaitClose {
            pubSub.punsubscribe()
        }
    }

    // Fechar recursos
    fun shutdown() {
        jedisPool.close()
        subscriberExecutor.shutdown()
    }
}

// Data classes para mensagens
data class PubSubMessage(
    val channel: String,
    val message: String,
    val type: MessageType,
    val pattern: String? = null
)

enum class MessageType {
    MESSAGE, SUBSCRIBE, UNSUBSCRIBE, PSUBSCRIBE, PUNSUBSCRIBE, PMESSAGE
}