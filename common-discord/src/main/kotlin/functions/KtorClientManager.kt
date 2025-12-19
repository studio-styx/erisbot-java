package functions

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicReference

class KtorClientManager {
    companion object {
        private val clientReference = AtomicReference<HttpClient?>(null)

        /**
         * Obtém o cliente HTTP Ktor existente ou cria um novo se não existir.
         *
         * @param recreate Se true, força a recriação do cliente mesmo se já existir
         * @return Instância do HttpClient
         */
        @Synchronized
        fun getClient(recreate: Boolean = false): HttpClient {
            return if (recreate) {
                createNewClient()
            } else {
                clientReference.get() ?: createNewClient()
            }
        }

        /**
         * Cria uma nova instância do cliente HTTP Ktor com configurações padrão.
         *
         * @return Nova instância do HttpClient configurada
         */
        private fun createNewClient(): HttpClient {
            return HttpClient(CIO) {
                // Timeout configuration
                install(HttpTimeout) {
                    requestTimeoutMillis = 30000
                    connectTimeoutMillis = 10000
                    socketTimeoutMillis = 30000
                }

                // JSON serialization
                install(ContentNegotiation) {
                    json(Json {
                        prettyPrint = true
                        isLenient = true
                        ignoreUnknownKeys = true
                    })
                }

                // Default request configuration
                defaultRequest {
                    header("Content-Type", "application/json")
                    header("Accept", "application/json")
                }
            }.also { newClient ->
                clientReference.set(newClient)
            }
        }

        /**
         * Fecha o cliente HTTP atual se existir.
         * Útil para limpeza ou reinicialização.
         */
        @Synchronized
        fun closeClient() {
            clientReference.getAndSet(null)?.close()
        }

        /**
         * Reinicia o cliente, fechando o atual e criando um novo.
         *
         * @return Nova instância do HttpClient
         */
        @Synchronized
        fun resetClient(): HttpClient {
            closeClient()
            return getClient()
        }

        /**
         * Verifica se já existe um cliente ativo.
         *
         * @return true se o cliente existir, false caso contrário
         */
        fun hasClient(): Boolean = clientReference.get() != null
    }
}

// Versão alternativa com function simples (se preferir uma abordagem mais direta)
object KtorClientSingleton {
    private var client: HttpClient? = null

    /**
     * Função direta para obter ou criar o cliente Ktor
     */
    fun getOrCreateClient(
        configure: HttpClientConfig<*>.() -> Unit = {}
    ): HttpClient {
        return client ?: synchronized(this) {
            client ?: HttpClient(CIO) {
                // Configurações padrão
                install(HttpTimeout) {
                    requestTimeoutMillis = 30000
                }
                install(ContentNegotiation) {
                    json(Json {
                        ignoreUnknownKeys = true
                    })
                }
                // Configurações personalizadas (se fornecidas)
                configure()
            }.also { client = it }
        }
    }

    fun close() {
        client?.close()
        client = null
    }
}
