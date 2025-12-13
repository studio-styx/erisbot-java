package server.core.security.model

data class DiscordTokenData(
    val access_token: String,
    val refresh_token: String,
    val expires_at: Long,
    val user_id: String
)

data class ApiTokenResponse(
    val access_token: String,
    val refresh_token: String,
    val expires_in: Long,
    val user_id: String,
    val token_type: String = "Bearer"
)