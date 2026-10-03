package me.rerere.rikkahub.web

import android.content.Context
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.http.Cookie
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpMethod
import io.ktor.server.application.createApplicationPlugin
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.request.httpMethod
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.FolderRepository
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.web.dto.ErrorResponse
import me.rerere.rikkahub.web.dto.WebAuthTokenRequest
import me.rerere.rikkahub.web.dto.WebAuthTokenResponse
import me.rerere.rikkahub.web.routes.aiIconRoutes
import me.rerere.rikkahub.web.routes.assetsRoutes
import me.rerere.rikkahub.web.routes.conversationRoutes
import me.rerere.rikkahub.web.routes.eventsRoutes
import me.rerere.rikkahub.web.routes.filesRoutes
import me.rerere.rikkahub.web.routes.folderRoutes
import me.rerere.rikkahub.web.routes.settingsRoutes
import java.security.MessageDigest
import java.util.Date
import java.util.UUID

private const val WEB_JWT_ISSUER = "rikkahub-web"
private const val WEB_JWT_AUDIENCE = "rikkahub-web-client"
private const val WEB_JWT_SUBJECT = "web-access"
private const val WEB_JWT_TTL_MILLIS = 24L * 60 * 60 * 1000
private const val WEB_AUTH_COOKIE = "rocl-web-auth"
private val webSigningKey = UUID.randomUUID().toString() + UUID.randomUUID().toString()
private const val WEB_AUTH_REALM = "rikkahub-web-api"

/**
 * Configure Web API for the Ktor application.
 * This should be called from app module when starting the web server.
 *
 * Example usage:
 * ```
 * startWebServer(port = 8080) {
 *     configureWebApi(context, chatService, conversationRepo, settingsStore, filesManager)
 * }
 * ```
 */
fun Application.configureWebApi(
    context: Context,
    chatService: ChatService,
    conversationRepo: ConversationRepository,
    folderRepo: FolderRepository,
    settingsStore: SettingsStore,
    filesManager: FilesManager
) {
    val jwtEnabled = settingsStore.settingsFlow.value.let { it.webServerJwtEnabled || !it.webServerLocalhostOnly }
    val authAttempts = ArrayDeque<Long>()

    install(createApplicationPlugin("WebCookieOriginCheck") {
        onCall { call ->
            if (call.request.cookies[WEB_AUTH_COOKIE] != null &&
                call.request.headers[HttpHeaders.Authorization] == null &&
                call.request.httpMethod !in listOf(HttpMethod.Get, HttpMethod.Head, HttpMethod.Options)
            ) {
                val origin = call.request.headers[HttpHeaders.Origin]
                val authority = origin?.let { runCatching { java.net.URI(it).rawAuthority }.getOrNull() }
                if (authority == null || authority != call.request.headers[HttpHeaders.Host]) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("Same-origin request required", 403))
                }
            }
        }
    })

    install(ContentNegotiation) {
        json(JsonInstant)
    }

    install(StatusPages) {
        status(HttpStatusCode.NotFound) { call, status ->
            call.respond(status, ErrorResponse("Not Found", status.value))
        }
        exception<ApiException> { call, cause ->
            call.respond(cause.status, ErrorResponse(cause.message, cause.status.value))
        }
        exception<Throwable> { call, cause ->
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(cause.message ?: "Internal server error", 500)
            )
        }
    }

    if (jwtEnabled) {
        install(Authentication) {
            jwt("auth-jwt") {
                realm = WEB_AUTH_REALM
                verifier { _ ->
                    // Dynamically read the current password on each request so that
                    // tokens signed after a password change are validated correctly.
                    val currentPassword = settingsStore.settingsFlow.value.webServerAccessPassword
                    val secret = currentPassword.ifBlank {
                        // Keep protected routes closed when jwt is enabled but password is missing.
                        "__missing_password_${UUID.randomUUID()}__"
                    }
                    buildWebJwtVerifier(secret)
                }
                authHeader { call ->
                    extractAccessToken(
                        authorizationHeader = call.request.headers[HttpHeaders.Authorization],
                        cookieToken = call.request.cookies[WEB_AUTH_COOKIE]
                    )?.let { token ->
                        HttpAuthHeader.Single("Bearer", token)
                    }
                }
                validate { credential ->
                    val currentPassword = settingsStore.settingsFlow.value.webServerAccessPassword
                    if (currentPassword.isBlank()) {
                        null
                    } else {
                        credential.payload.subject?.takeIf { it == WEB_JWT_SUBJECT }?.let {
                            io.ktor.server.auth.jwt.JWTPrincipal(credential.payload)
                        }
                    }
                }
                challenge { _, _ ->
                    val currentPassword = settingsStore.settingsFlow.value.webServerAccessPassword
                    if (currentPassword.isBlank()) {
                        call.respond(
                            HttpStatusCode.Forbidden,
                            ErrorResponse("Access password is not configured", HttpStatusCode.Forbidden.value)
                        )
                    } else {
                        call.respond(
                            HttpStatusCode.Unauthorized,
                            ErrorResponse("Unauthorized", HttpStatusCode.Unauthorized.value)
                        )
                    }
                }
            }
        }
    }

    routing {
        route("/api") {
            post("/auth/logout") {
                call.response.cookies.append(Cookie(
                    name = WEB_AUTH_COOKIE, value = "", path = "/api", httpOnly = true, maxAge = 0,
                    extensions = mapOf("SameSite" to "Strict"),
                ))
                call.respond(HttpStatusCode.OK)
            }
            post("/auth/token") {
                val now = System.currentTimeMillis()
                val allowed = synchronized(authAttempts) {
                    while (authAttempts.isNotEmpty() && now - authAttempts.first() >= 60_000) authAttempts.removeFirst()
                    if (authAttempts.size >= 5) false else { authAttempts.addLast(now); true }
                }
                if (!allowed) {
                    call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("Try again later", 429))
                    return@post
                }
                val settings = settingsStore.settingsFlow.value
                if (!jwtEnabled) {
                    throw BadRequestException("JWT auth is disabled")
                }

                val accessPassword = settings.webServerAccessPassword
                if (accessPassword.isBlank()) {
                    throw BadRequestException("Access password is not configured")
                }

                val request = call.receive<WebAuthTokenRequest>()
                if (!secureEquals(request.password, accessPassword)) {
                    throw UnauthorizedException("Invalid password")
                }

                val (token, expiresAt) = createWebJwt(accessPassword)
                call.response.cookies.append(Cookie(
                    name = WEB_AUTH_COOKIE, value = token, path = "/api", httpOnly = true,
                    maxAge = (WEB_JWT_TTL_MILLIS / 1000).toInt(), extensions = mapOf("SameSite" to "Strict"),
                ))
                call.respond(
                    HttpStatusCode.OK,
                    WebAuthTokenResponse(
                        token = token,
                        expiresAt = expiresAt
                    )
                )
            }

            aiIconRoutes(context)

            if (jwtEnabled) {
                authenticate("auth-jwt") {
                    conversationRoutes(chatService, conversationRepo, folderRepo, settingsStore)
                    folderRoutes(chatService, folderRepo, settingsStore)
                    eventsRoutes(chatService, conversationRepo, folderRepo, settingsStore)
                    settingsRoutes(settingsStore)
                    filesRoutes(filesManager, context)
                    assetsRoutes(context)
                }
            } else {
                conversationRoutes(chatService, conversationRepo, folderRepo, settingsStore)
                folderRoutes(chatService, folderRepo, settingsStore)
                eventsRoutes(chatService, conversationRepo, folderRepo, settingsStore)
                settingsRoutes(settingsStore)
                filesRoutes(filesManager, context)
                assetsRoutes(context)
            }
        }
    }
}

private fun createWebJwt(secret: String): Pair<String, Long> {
    val now = System.currentTimeMillis()
    val expiresAt = now + WEB_JWT_TTL_MILLIS
    val token = JWT.create()
        .withIssuer(WEB_JWT_ISSUER)
        .withAudience(WEB_JWT_AUDIENCE)
        .withSubject(WEB_JWT_SUBJECT)
        .withIssuedAt(Date(now))
        .withExpiresAt(Date(expiresAt))
        .sign(Algorithm.HMAC256(webSigningKey + secret))
    return token to expiresAt
}

private fun buildWebJwtVerifier(secret: String): JWTVerifier {
    return JWT.require(Algorithm.HMAC256(webSigningKey + secret))
        .withIssuer(WEB_JWT_ISSUER)
        .withAudience(WEB_JWT_AUDIENCE)
        .withSubject(WEB_JWT_SUBJECT)
        .build()
}

private fun extractBearerToken(authorizationHeader: String?): String? {
    if (authorizationHeader.isNullOrBlank()) return null
    val prefix = "Bearer "
    if (!authorizationHeader.startsWith(prefix, ignoreCase = true)) return null
    return authorizationHeader.substring(prefix.length).trim().takeIf { it.isNotEmpty() }
}

private fun extractAccessToken(authorizationHeader: String?, cookieToken: String?): String? {
    return extractBearerToken(authorizationHeader)
        ?: cookieToken?.trim()?.takeIf { it.isNotEmpty() }
}

private fun secureEquals(left: String, right: String): Boolean {
    return MessageDigest.isEqual(left.toByteArray(Charsets.UTF_8), right.toByteArray(Charsets.UTF_8))
}
