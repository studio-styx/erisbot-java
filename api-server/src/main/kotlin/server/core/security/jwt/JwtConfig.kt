package server.core.security.jwt

import com.auth0.jwt.algorithms.Algorithm

object JwtConfig {
    const val issuer = "studio.styx.erisbot"
    const val audience = "eris-api"
    private val secret = System.getenv("JWT_SECRET") ?: "default-secret"

    val algorithm = Algorithm.HMAC256(secret)
}