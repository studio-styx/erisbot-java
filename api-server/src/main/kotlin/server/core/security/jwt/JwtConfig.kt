package server.core.security.jwt

import com.auth0.jwt.algorithms.Algorithm
import shared.utils.Env

object JwtConfig {
    const val issuer = "studio.styx.erisbot"
    const val audience = "eris-api"
    val secret = Env.get("JWT_SECRET", "default-secret")

    val algorithm = Algorithm.HMAC256(secret)
}