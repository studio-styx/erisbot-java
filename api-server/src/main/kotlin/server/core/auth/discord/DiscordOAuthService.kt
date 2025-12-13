package server.core.auth.discord

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import shared.utils.Env

// Modelo para a resposta da API do Discord
@Serializable
data class DiscordTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expiresIn: Int,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("scope") val scope: String,
    @SerialName("user_id") val userId: String? = null
)

// Resultado padronizado da operação
sealed class TokenExchangeResult {
    data class Success(val data: DiscordTokenResponse) : TokenExchangeResult()
    data class Error(val message: String, val status: Int? = null) : TokenExchangeResult()
}

class DiscordOAuthService(
    private val client: HttpClient,
    private val clientId: String = Env.get("CLIENT_ID").toString(),
    private val clientSecret: String = Env.get("CLIENT_SECRET").toString(),
    private val redirectUri: String = Env.get("SERVER_BASE_URL").toString() + "/auth/redirect"
) {
    suspend fun exchangeCodeForToken(code: String): TokenExchangeResult {
        return makeTokenRequest("authorization_code", "code" to code)
    }

    suspend fun refreshAccessToken(refreshToken: String): TokenExchangeResult {
        return makeTokenRequest("refresh_token", "refresh_token" to refreshToken)
    }

    private suspend fun makeTokenRequest(
        grantType: String,
        vararg extraParams: Pair<String, String>
    ): TokenExchangeResult {
        return try {
            // Constrói o corpo da requisição como "application/x-www-form-urlencoded"
            val parameters = Parameters.build {
                append("client_id", clientId)
                append("client_secret", clientSecret)
                append("grant_type", grantType)

                extraParams.forEach { (key, value) ->
                    append(key, value)
                }

                // Para authorization_code, adiciona o redirect_uri
                if (grantType == "authorization_code") {
                    append("redirect_uri", redirectUri)
                }
            }

            // Faz a requisição POST
            val response = client.submitForm(
                url = "https://discord.com/api/oauth2/token",
                formParameters = parameters
            ) {
                contentType(ContentType.Application.FormUrlEncoded)
            }

            // Verifica se a resposta foi bem-sucedida
            if (response.status.isSuccess()) {
                val tokenData = response.body<DiscordTokenResponse>()
                TokenExchangeResult.Success(tokenData)
            } else {
                TokenExchangeResult.Error(
                    "Discord API error: ${response.status.description}",
                    response.status.value
                )
            }
        } catch (e: ClientRequestException) {
            // Erro 4xx da API
            TokenExchangeResult.Error(
                "Discord API client error: ${e.message}",
                e.response.status.value
            )
        } catch (e: ServerResponseException) {
            // Erro 5xx da API
            TokenExchangeResult.Error(
                "Discord API server error: ${e.message}",
                e.response.status.value
            )
        } catch (e: Exception) {
            // Outros erros (network, serialization, etc)
            TokenExchangeResult.Error("Network or processing error: ${e.message}")
        }
    }
}