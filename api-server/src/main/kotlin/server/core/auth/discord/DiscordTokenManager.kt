package server.core.auth.discord

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import shared.utils.Env
import java.time.LocalDateTime

class DiscordTokenManager(
    private val discordOAuthService: DiscordOAuthService,
    private val authStorage: AuthStorageService
) {
    private val refreshMutex = Mutex()

    suspend fun getValidToken(userId: String): DiscordTokenResponse? {
        return refreshMutex.withLock {
            val stored = authStorage.getDiscordToken(userId)

            stored?.let {
                // Verifica se está prestes a expirar (menos de 5 minutos)
                val isAboutToExpire = LocalDateTime.now()
                    .plusMinutes(5)
                    .isAfter(it.getExpiresAt())

                if (isAboutToExpire) {
                    // Renova o token antecipadamente
                    return@withLock refreshToken(userId)
                }
            }

            stored
        }
    }

    suspend fun refreshToken(userId: String): DiscordTokenResponse? {
        val stored = authStorage.getStoredAuth(userId) ?: return null

        return runCatching {
            val refreshToken = EncryptionService(Env.get("ENCRYPTION_KEY", "default-encryption-key")).decrypt(stored.refreshTokenEncrypted)
            when (val result = discordOAuthService.refreshAccessToken(refreshToken)) {
                is TokenExchangeResult.Success -> {
                    authStorage.setAuth(userId, result.data)
                    result.data
                }
                is TokenExchangeResult.Error -> {
                    null
                }
            }
        }.getOrNull()
    }

    private fun DiscordTokenResponse.getExpiresAt(): LocalDateTime {
        return LocalDateTime.now().plusSeconds(this.expiresIn.toLong())
    }
}