package server.routes.auth

import io.ktor.client.HttpClient
import io.ktor.http.Cookie
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import org.jooq.DSLContext
import server.core.auth.discord.DatabaseAuthStorageService
import server.core.auth.discord.DiscordOAuthService
import server.core.auth.discord.TokenExchangeResult
import server.core.security.jwt.JwtManager
import shared.utils.Env

fun Route.authRoutes(dsl: DSLContext, httpClient: HttpClient) {
    val oAuthService = DiscordOAuthService(client = httpClient)
    val storageService = DatabaseAuthStorageService(dsl)

    route("/auth/discord") {
        get("/redirect") {
            try {
                val code = call.request.queryParameters["code"]
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Missing authorization code")
                    )

                // 1. Trocar código por token do Discord
                when (val tokenResult = oAuthService.exchangeCodeForToken(code)) {
                    is TokenExchangeResult.Error -> {
                        call.respond(
                            HttpStatusCode.fromValue(tokenResult.status ?: 400),
                            mapOf("error" to tokenResult.message)
                        )
                        return@get
                    }
                    is TokenExchangeResult.Success -> {
                        val data = tokenResult.data

                        val userId = data.userId ?: run {
                            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing user id"))
                            return@get
                        }

                        // 2. Armazenar tokens do Discord no banco
                        val authStored = storageService.setAuth(userId, data)

                        if (!authStored) {
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                mapOf("error" to "Failed to store authentication data")
                            )
                            return@get
                        }

                        // 3. Gerar tokens JWT da sua aplicação
                        val accessToken = JwtManager.generateAccessToken(
                            userId = userId,
                            discordToken = data.accessToken,
                            discordRefreshToken = data.refreshToken,
                            discordExpiresAt = data.expiresIn.toLong()
                        )

                        val refreshToken = JwtManager.generateRefreshToken(userId)

                        // 4. Determinar URL de redirecionamento baseado no ambiente
                        val isDev = Env.get("ENV").toString() == "dev"

                        val redirectUrl = Env.get("FRONT_URL").toString()

                        // 5. Configurar cookies HTTP-only seguros

                        // Cookie do access token (curta duração)
                        call.response.cookies.append(
                            Cookie(
                                name = "access_token",
                                value = accessToken,
                                secure = !isDev, // HTTPS apenas em produção
                                httpOnly = true,
                                path = "/",
                                maxAge = 3600, // 1 hora em segundos
                                extensions = mapOf("SameSite" to "Lax")
                            )
                        )

                        // Cookie do refresh token (longa duração)
                        call.response.cookies.append(
                            Cookie(
                                name = "refresh_token",
                                value = refreshToken,
                                secure = !isDev,
                                httpOnly = true,
                                path = "/api/auth/refresh",
                                maxAge = 7 * 24 * 3600, // 7 dias em segundos
                                extensions = mapOf("SameSite" to "Strict")
                            )
                        )

                        // Cookie não sensível para o frontend saber se está autenticado
                        call.response.cookies.append(
                            Cookie(
                                name = "is_authenticated",
                                value = "true",
                                secure = !isDev,
                                httpOnly = false, // Frontend pode ler
                                path = "/",
                                maxAge = 3600,
                                extensions = mapOf("SameSite" to "Lax")
                            )
                        )

                        call.respondRedirect(redirectUrl)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Internal server error: ${e.message}")
                )
            }
        }
    }
}
