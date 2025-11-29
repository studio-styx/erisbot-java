package server.core.security.jwt

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date

object JwtManage {
    private val secret = System.getenv("JWT_SECRET") ?: throw NullPointerException("JWT_SECRET não encontrada")
    private const val issuer = "meu-issuer"
    private const val audience = "meu-audience"
    private const val validityInMs = 36_000_00 * 10  // 10 horas

    fun generateToken(userId: String): String {
        val now = System.currentTimeMillis()
        val expire = Date(now + validityInMs)

        return JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withSubject(userId)
            .withExpiresAt(expire)
            .sign(Algorithm.HMAC256(secret))
    }
}
