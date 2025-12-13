package server.core.security.jwt

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.auth.jwt.JWTPrincipal
import server.core.security.model.ApiTokenResponse
import server.core.security.model.DiscordTokenData
import java.util.*

object JwtManager {
    private val secret = JwtConfig.secret
    private const val issuer = JwtConfig.issuer
    private const val audience = JwtConfig.audience
    private const val accessTokenValidity = 3_600_000L  // 1 hora
    private const val refreshTokenValidity = 7 * 24 * 3_600_000L  // 7 dias

    fun generateAccessToken(
        userId: String,
        discordToken: String? = null,
        discordRefreshToken: String? = null,
        discordExpiresAt: Long? = null
    ): String {
        val now = System.currentTimeMillis()
        val expire = Date(now + accessTokenValidity)

        val jwtBuilder = JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withSubject(userId)
            .withClaim("user_id", userId)
            .withExpiresAt(expire)
            .withIssuedAt(Date(now))

        // Adiciona claims do Discord se fornecidas
        discordToken?.let { jwtBuilder.withClaim("discord_token", it) }
        discordRefreshToken?.let { jwtBuilder.withClaim("discord_refresh_token", it) }
        discordExpiresAt?.let { jwtBuilder.withClaim("discord_expires_at", it) }

        return jwtBuilder.sign(Algorithm.HMAC256(secret))
    }

    fun generateRefreshToken(userId: String): String {
        val now = System.currentTimeMillis()
        val expire = Date(now + refreshTokenValidity)

        return JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withSubject("refresh_$userId")
            .withClaim("user_id", userId)
            .withClaim("token_type", "refresh")
            .withExpiresAt(expire)
            .withIssuedAt(Date(now))
            .sign(Algorithm.HMAC256(secret))
    }

    fun generateTokensFromDiscord(discordData: DiscordTokenData): ApiTokenResponse {
        val accessToken = generateAccessToken(
            userId = discordData.user_id,
            discordToken = discordData.access_token,
            discordRefreshToken = discordData.refresh_token,
            discordExpiresAt = discordData.expires_at
        )

        val refreshToken = generateRefreshToken(discordData.user_id)

        return ApiTokenResponse(
            access_token = accessToken,
            refresh_token = refreshToken,
            expires_in = accessTokenValidity / 1000, // em segundos
            user_id = discordData.user_id
        )
    }

    fun verifyToken(token: String): JWTPrincipal? {
        return try {
            val verifier = JWT.require(Algorithm.HMAC256(secret))
                .withIssuer(issuer)
                .withAudience(audience)
                .build()

            val decoded = verifier.verify(token)
            JWTPrincipal(decoded)
        } catch (e: Exception) {
            null
        }
    }
}