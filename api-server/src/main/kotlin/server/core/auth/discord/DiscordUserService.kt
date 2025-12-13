package server.core.auth.discord

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

@Serializable
data class DiscordUser(
    val id: String,
    val username: String,
    val discriminator: String? = null, // Discord está removendo discriminators
    val global_name: String? = null,
    val avatar: String? = null,
    val banner: String? = null,
    val accent_color: Int? = null,
    val verified: Boolean? = null,
    val email: String? = null,
    val flags: Int? = null,
    val premium_type: Int? = null,
    val public_flags: Int? = null
)

sealed class FetchUserInfoResult {
    data class Success(val user: DiscordUser) : FetchUserInfoResult()
    data class Error(val message: String, val status: Int? = null) : FetchUserInfoResult()
}

class DiscordUserService(private val client: HttpClient) {

    suspend fun fetchUserInfo(accessToken: String): FetchUserInfoResult {
        return try {
            val response = client.get("https://discord.com/api/v10/users/@me") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $accessToken")
                    append(HttpHeaders.Accept, "application/json")
                }
            }

            if (response.status.isSuccess()) {
                val user = response.body<DiscordUser>()
                FetchUserInfoResult.Success(user)
            } else {
                FetchUserInfoResult.Error(
                    "Failed to fetch user info: ${response.status.description}",
                    response.status.value
                )
            }
        } catch (e: ClientRequestException) {
            FetchUserInfoResult.Error(
                "Discord API client error: ${e.message}",
                e.response.status.value
            )
        } catch (e: ServerResponseException) {
            FetchUserInfoResult.Error(
                "Discord API server error: ${e.message}",
                e.response.status.value
            )
        } catch (e: Exception) {
            FetchUserInfoResult.Error("Network or processing error: ${e.message}")
        }
    }
}