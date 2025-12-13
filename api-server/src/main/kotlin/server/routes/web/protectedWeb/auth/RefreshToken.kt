package server.routes.web.protectedWeb.auth

import io.ktor.http.Cookie
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import net.dv8tion.jda.api.JDA
import org.jooq.DSLContext
import server.core.security.jwt.JwtManager

fun Route.refreshToken(dsl: DSLContext, jda: JDA) {
    get("/refresh") {
        // 1. PEGAR O COOKIE
        val refreshToken = call.request.cookies["refresh_token"]

        if (refreshToken == null) {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Refresh token não encontrado"))
            return@get
        }

        // 1. Verifica e faz o Smart Cast de nulo
        val decodedJWT = JwtManager.verifyToken(refreshToken)
        if (decodedJWT == null) {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Refresh token inválido"))
            return@get
        }

        // 2. Agora 'decodedJWT' não é nulo.
        // Acesse '.payload.getClaim' em vez de '.getClaim'.
        // Adicione <Map<String, String>> no respond para corrigir o erro do tipo 'T'.
        if (decodedJWT.payload.getClaim("token_type").asString() != "refresh") {
            call.respond<Map<String, String>>(
                HttpStatusCode.Unauthorized,
                mapOf("error" to "Token fornecido não é um refresh token")
            )
            return@get
        }

        val userId = decodedJWT.payload.getClaim("user_id").asString()

        // 3. (OPCIONAL MAS RECOMENDADO) VERIFICAR NO BANCO
        // Verifique se o usuário ainda existe ou se o token não foi revogado na tabela USERTOKEN
        // val storedAuth = authStorage.getStoredAuth(userId) ...

        // 4. GERAR NOVO ACCESS TOKEN
        // Nota: Aqui assumimos que não precisamos renovar o token do Discord agora,
        // apenas o da sua API. Se precisar dos claims do Discord, teria que buscar no banco.
        val newAccessToken = JwtManager.generateAccessToken(userId)

        // 5. DEFINIR O NOVO COOKIE DE ACCESS TOKEN
        val isDev = System.getenv("ENV") == "dev" // Mesma lógica do AuthRoutes

        call.response.cookies.append(
            Cookie(
                name = "access_token",
                value = newAccessToken,
                secure = !isDev,
                httpOnly = true,
                path = "/", // Access token vale para todo o site
                maxAge = 3600,
                extensions = mapOf("SameSite" to "Lax")
            )
        )

        // 6. RESPONDER SUCESSO
        call.respond(HttpStatusCode.OK, mapOf("message" to "Token renovado com sucesso"))
    }
}