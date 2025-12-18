package functions.football

import dtos.football.footballData.api.fixtureResult.competition.Competition
import dtos.football.footballData.api.fixtureResult.match.Match
import dtos.football.footballData.api.fixtureResult.match.Matches
import dtos.football.footballData.api.fixtureResult.team.Club
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import shared.utils.Env

import kotlinx.coroutines.*
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

val envApiKey = Env.get("API_FOOTBALL_DATA_KEY").toString()

class RequestQueue {
    private val queue = mutableListOf<QueuedTask>()
    private var processing = false
    private var lastRequest = 0L
    private val minInterval = 6000L // 6 segundos

    suspend fun <T> add(task: suspend () -> T): T {
        return suspendCancellableCoroutine { continuation ->
            synchronized(this) {
                queue.add(QueuedTaskImpl(task, continuation))
                if (!processing) {
                    processing = true
                    CoroutineScope(Dispatchers.Default).launch {
                        processQueue()
                    }
                }
            }
        }
    }

    private suspend fun processQueue() {
        while (true) {
            val task = synchronized(this) {
                if (queue.isEmpty()) {
                    processing = false
                    return
                }
                queue.removeAt(0)
            }

            awaitDelay()

            try {
                task.run()
            } finally {
                lastRequest = System.currentTimeMillis()
            }
        }
    }

    private suspend fun awaitDelay() {
        val now = System.currentTimeMillis()
        val elapsed = now - lastRequest
        if (elapsed < minInterval) {
            val delayTime = minInterval - elapsed
            delay(delayTime)
        }
    }

    private abstract class QueuedTask {
        abstract suspend fun run()
    }

    private class QueuedTaskImpl<T>(
        private val task: suspend () -> T,
        private val continuation: Continuation<T>
    ) : QueuedTask() {
        override suspend fun run() {
            try {
                val result = task()
                continuation.resume(result)
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }
    }
}

val requestQueue = RequestQueue()

interface FootballCache {
    suspend fun <T> get(key: String, clazz: Class<T>): T?
    suspend fun <T> set(key: String, value: T, expirationSeconds: Long)
}

class ApiFootballDataSdk(
    @PublishedApi internal val client: HttpClient,
    apiKey: String? = null,
    private val cache: FootballCache? = null
) {
    @PublishedApi internal val baseUrl = "https://api.football-data.org/v4"
    @PublishedApi internal val apiKey = apiKey ?: envApiKey

    // --- Módulos ---
    val matches = MatchesModule()
    val teams = TeamsModule()
    val competitions = CompetitionsModule()

    @PublishedApi
    internal suspend inline fun <reified T> handleRateLimit(
        error: Throwable,
        retryRequest: suspend () -> T
    ): T {
        if (error is ResponseException && error.response.status == HttpStatusCode.TooManyRequests) {
            val retryAfter = error.response.headers["Retry-After"]?.toIntOrNull() ?: 60
            val delayMillis = retryAfter * 1000L

            println("Rate limit detectado! Aguardando ${retryAfter}s antes de retry...")
            delay(delayMillis)
            return retryRequest()
        }
        throw error
    }

    // --- Implementação do Módulo de Partidas ---
    inner class MatchesModule {
        suspend fun get(id: Long): Match =
            request(RequestConfig("matches/$id"))

        suspend fun getAndUseCache(id: Long): Match {
            val key = "football:match:$id"
            val cached = cache?.get(key, Match::class.java)
            if (cached != null) return cached

            val response: Match = get(id)
            cache?.set(key, response, 3600)
            return response
        }

        suspend fun getTodayGames(): Matches =
            request(RequestConfig("matches"))

        suspend fun getGamesByRange(dateFrom: LocalDate, dateTo: LocalDate): Matches =
            request(RequestConfig("matches", params = mapOf(
                "dateFrom" to dateFrom.format(DateTimeFormatter.ISO_LOCAL_DATE),
                "dateTo" to dateTo.format(DateTimeFormatter.ISO_LOCAL_DATE)
            )))

        suspend fun getGamesByRange(dateFrom: OffsetDateTime, dateTo: OffsetDateTime): Matches =
            request(RequestConfig("matches", params = mapOf(
                "dateFrom" to dateFrom.format(DateTimeFormatter.ISO_LOCAL_DATE),
                "dateTo" to dateTo.format(DateTimeFormatter.ISO_LOCAL_DATE)
            )))

        suspend fun getMany(
            dateFrom: LocalDate? = null,
            dateTo: LocalDate? = null,
            season: Int? = null,
            competitions: List<String>? = null,
            status: String? = null,
            limit: Int? = null
        ): Matches {
            val params = mutableMapOf<String, String>().apply {
                dateFrom?.let { put("dateFrom", it.format(DateTimeFormatter.ISO_LOCAL_DATE)) }
                dateTo?.let { put("dateTo", it.format(DateTimeFormatter.ISO_LOCAL_DATE)) }
                season?.let { put("season", it.toString()) }
                competitions?.let { put("competitions", it.joinToString(",")) }
                status?.let { put("status", it) }
                limit?.let { put("limit", it.toString()) }
            }
            return request(RequestConfig("matches", params = params))
        }
    }

    // --- Implementação do Módulo de Times ---
    inner class TeamsModule {
        fun get(id: Long) = TeamActions(id)

        inner class TeamActions(val id: Long) {
            suspend fun getInfo(): Club =
                request(RequestConfig("teams/$id"))

            suspend fun getMatches(status: String? = null, limit: Int? = null): Matches {
                val params = mutableMapOf<String, String>().apply {
                    status?.let { put("status", it) }
                    limit?.let { put("limit", it.toString()) }
                }
                return request(RequestConfig("teams/$id/matches", params = params))
            }
        }
    }

    // --- Implementação do Módulo de Competições ---
    inner class CompetitionsModule {
        fun get(code: String) = CompetitionActions(code)

        inner class CompetitionActions(val code: String) {
            suspend fun getInfo(): Competition =
                request(RequestConfig("competitions/$code"))

            suspend fun getMatchday(matchday: Int): Matches =
                request(RequestConfig("competitions/$code/matches", params = mapOf("matchday" to matchday.toString())))
        }
    }

    // --- Utilitários de Requisição ---

    data class RequestConfig(
        val url: String,
        val method: HttpMethod = HttpMethod.Get,
        val params: Map<String, String> = emptyMap(),
        val headers: Map<String, String> = emptyMap(),
        val body: Any? = null
    )

    suspend inline fun <reified T> request(config: RequestConfig): T {
        return requestQueue.add {
            try {
                executeRequest<T>(config)
            } catch (error: Throwable) {
                handleRateLimit(error) {
                    executeRequest<T>(config)
                }
            }
        }
    }

    @PublishedApi
    internal suspend inline fun <reified T> executeRequest(config: RequestConfig): T {
        return client.request("$baseUrl/${config.url}") {
            method = config.method
            header("X-Auth-Token", apiKey)
            config.headers.forEach { (k, v) -> header(k, v) }
            config.params.forEach { (k, v) -> url.parameters.append(k, v) }
            if (config.body != null) setBody(config.body)
        }.body()
    }
}

val apiFootballSdk = ApiFootballDataSdk(HttpClient(), apiKey = envApiKey)