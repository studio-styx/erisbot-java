package server.core.auth.discord

import org.jooq.DSLContext
import shared.utils.Env
import studio.styx.erisbot.generated.tables.references.USERTOKEN
import java.time.LocalDateTime
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.IvParameterSpec
import kotlin.text.Charsets.UTF_8

data class StoredTokenData(
    val userId: String,
    val refreshTokenEncrypted: String,  // Refresh token encriptado
    val expiresAt: LocalDateTime,
    val createdAt: LocalDateTime = LocalDateTime.now()
)

interface AuthStorageService {
    fun setAuth(userId: String, tokenData: DiscordTokenResponse): Boolean
    fun getStoredAuth(userId: String): StoredTokenData?
    fun getDiscordToken(userId: String): DiscordTokenResponse?
    fun updateToken(userId: String, newToken: DiscordTokenResponse): Boolean
    fun deleteAuth(userId: String): Boolean
}

class DatabaseAuthStorageService(
    private val dsl: DSLContext,
    encryptionKey: String = Env.get("ENCRYPTION_KEY", "default-encryption-key")
) : AuthStorageService {

    private val encryptionService = EncryptionService(encryptionKey)

    override fun setAuth(userId: String, tokenData: DiscordTokenResponse): Boolean {
        val expiresAt = LocalDateTime.now().plusSeconds(tokenData.expiresIn.toLong())
        val encryptedRefreshToken = encryptionService.encrypt(tokenData.refreshToken)

        try {
            val encryptedAccessToken = encryptionService.encrypt(tokenData.accessToken)

            dsl.insertInto(USERTOKEN)
                .set(USERTOKEN.USERID, userId)
                .set(USERTOKEN.REFRESH_TOKEN, encryptedRefreshToken)
                .set(USERTOKEN.ACCESS_TOKEN, encryptedAccessToken)
                .set(USERTOKEN.EXPIRESIN, expiresAt)
                .set(USERTOKEN.TOKEN_TYPE, tokenData.tokenType)
                .set(USERTOKEN.SCOPE, tokenData.scope)
                .onConflict(USERTOKEN.USERID)
                .doUpdate()
                .set(USERTOKEN.REFRESH_TOKEN, encryptedRefreshToken)
                .set(USERTOKEN.ACCESS_TOKEN, encryptedAccessToken)
                .set(USERTOKEN.EXPIRESIN, expiresAt)
                .set(USERTOKEN.TOKEN_TYPE, tokenData.tokenType)
                .set(USERTOKEN.SCOPE, tokenData.scope)
                .execute()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    override fun getStoredAuth(userId: String): StoredTokenData? {
        return try {
            val record = dsl.selectFrom(USERTOKEN)
                .where(USERTOKEN.USERID.eq(userId))
                .fetchOne()

            record?.let {
                StoredTokenData(
                    userId = it[USERTOKEN.USERID]!!,
                    refreshTokenEncrypted = it[USERTOKEN.REFRESH_TOKEN]!!,
                    expiresAt = it[USERTOKEN.EXPIRESIN]!!
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun getDiscordToken(userId: String): DiscordTokenResponse? {
        return try {
            val record = dsl.selectFrom(USERTOKEN)
                .where(USERTOKEN.USERID.eq(userId))
                .fetchOne()

            record?.let {
                val refreshToken = encryptionService.decrypt(it[USERTOKEN.REFRESH_TOKEN]!!)
                val accessToken = it[USERTOKEN.ACCESS_TOKEN]?.let { encrypted ->
                    encryptionService.decrypt(encrypted)
                }

                // Se não tivermos access token armazenado ou estiver expirado,
                // precisamos usar o DiscordOAuthService para obter um novo
                val isExpired = it[USERTOKEN.EXPIRESIN]!!.isBefore(LocalDateTime.now())

                if (accessToken == null || isExpired) {
                    return null // Precisa renovar
                }

                DiscordTokenResponse(
                    accessToken = accessToken,
                    tokenType = it[USERTOKEN.TOKEN_TYPE]!!,
                    expiresIn = calculateRemainingSeconds(it[USERTOKEN.EXPIRESIN]!!),
                    refreshToken = refreshToken,
                    scope = it[USERTOKEN.SCOPE]!!
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun updateToken(userId: String, newToken: DiscordTokenResponse): Boolean {
        return setAuth(userId, newToken)
    }

    override fun deleteAuth(userId: String): Boolean {
        try {
            dsl.deleteFrom(USERTOKEN)
                .where(USERTOKEN.USERID.eq(userId))
                .execute()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun calculateRemainingSeconds(expiresAt: LocalDateTime): Int {
        val now = LocalDateTime.now()
        return if (expiresAt.isAfter(now)) {
            (expiresAt.seconds - now.seconds).toInt()
        } else {
            0
        }
    }
}

// Serviço de encriptação
class EncryptionService(private val secretKey: String) {

    private val algorithm = "AES/CBC/PKCS5Padding"
    private val keyLength = 256
    private val iterationCount = 65536
    private val salt = "fixed-salt-for-consistency".toByteArray(UTF_8) // Em produção, use um salt único por token

    private fun generateKey(): javax.crypto.SecretKey {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(secretKey.toCharArray(), salt, iterationCount, keyLength)
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    fun encrypt(data: String): String {
        val cipher = Cipher.getInstance(algorithm)
        val key = generateKey()
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(data.toByteArray(UTF_8))

        // Combina IV + dados encriptados em base64
        val combined = iv + encrypted
        return Base64.getEncoder().encodeToString(combined)
    }

    fun decrypt(encryptedData: String): String {
        val combined = Base64.getDecoder().decode(encryptedData)
        val iv = combined.copyOfRange(0, 16) // IV é sempre 16 bytes para AES/CBC
        val encrypted = combined.copyOfRange(16, combined.size)

        val cipher = Cipher.getInstance(algorithm)
        val key = generateKey()
        cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
        val decrypted = cipher.doFinal(encrypted)

        return String(decrypted, UTF_8)
    }
}

// Extensão para converter LocalDateTime para segundos desde epoch
private val LocalDateTime.seconds: Long
    get() = this.atZone(java.time.ZoneId.systemDefault()).toEpochSecond()