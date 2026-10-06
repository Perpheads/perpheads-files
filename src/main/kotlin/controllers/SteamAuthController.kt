package com.perpheads.files.controllers

import com.fasterxml.jackson.databind.DeserializationFeature
import com.perpheads.files.repository.CookieRepository
import com.perpheads.files.repository.UserRepository
import com.perpheads.files.suspending
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.netty.handler.codec.http.HttpHeaderNames
import io.quarkus.runtime.LaunchMode
import io.quarkus.security.UnauthorizedException
import io.smallrye.mutiny.Uni
import io.vertx.ext.web.RoutingContext
import jakarta.annotation.security.PermitAll
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.NewCookie
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.UriBuilder
import jakarta.ws.rs.core.UriInfo
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.HexFormat

private const val STEAM_OPENID_ENDPOINT = "https://steamcommunity.com/openid/login"
private const val STEAM_CALLBACK_PATH = "/api/user/steam/steam-callback"

@Path("/api/user/steam")
class SteamAuthController(
    private val cookieRepository: CookieRepository,
    private val userRepository: UserRepository
) {

    private val secureRandom = SecureRandom()

    private val httpClient = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 10000
            connectTimeoutMillis = 5000
            socketTimeoutMillis = 5000
        }
        install(ContentNegotiation) {
            jackson {
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
    }

    /**
     * The callback under the address of the request. Steam returns there, and logins are only accepted for exactly
     * this address. That relies on the Host header being genuine: behind cloudflared only the own hostnames get
     * through, so the port must not be reachable directly.
     */
    private fun steamCallbackUrl(context: RoutingContext): String =
        context.request().scheme() + "://" + context.request().authority() + STEAM_CALLBACK_PATH

    @GET
    @PermitAll
    fun steam(
        @Context context: RoutingContext
    ): Response {
        val returnUrl = steamCallbackUrl(context)
        val url = UriBuilder.newInstance()
            .host("steamcommunity.com")
            .scheme("https")
            .path("/openid/login").apply {
                queryParam("openid.ns", "http://specs.openid.net/auth/2.0")
                queryParam("openid.mode", "checkid_setup")
                queryParam("openid.return_to", returnUrl)
                queryParam("openid.realm", returnUrl)
                queryParam("openid.identity", "http://specs.openid.net/auth/2.0/identifier_select")
                queryParam("openid.claimed_id", "http://specs.openid.net/auth/2.0/identifier_select")
            }.build()

        return Response.status(HttpStatusCode.Found.value)
            .header(HttpHeaderNames.LOCATION.toString(), url)
            .build()
    }

    /**
     * The community id of the player Steam confirmed the login for, or null if it isn't valid. [expectedReturnUrl] is
     * the callback the login must have been made for: Steam only confirms that a login is genuine, not for which site,
     * so a login made on another site (which that site could pass on) must not be accepted here.
     */
    suspend fun verifySteamAuthRequest(uriInfo: UriInfo, expectedReturnUrl: String): Long? {
        val params = uriInfo.queryParameters
        val steamIdParam = params["openid.claimed_id"]?.singleOrNull() ?: return null
        if (params.getFirst("openid.mode") != "id_res") return null
        if (params.getFirst("openid.op_endpoint") != STEAM_OPENID_ENDPOINT) return null
        if (params.getFirst("openid.return_to") != expectedReturnUrl) return null
        if (params.getFirst("openid.identity") != steamIdParam) return null
        // The checked values must be covered by Steam's signature, which check_authentication verifies below
        val signed = params.getFirst("openid.signed")?.split(',')?.toSet() ?: return null
        if (!signed.containsAll(listOf("op_endpoint", "return_to", "claimed_id", "identity"))) return null

        val steamIdMatch = "https://steamcommunity.com/openid/id/(\\d+)".toRegex().matchEntire(steamIdParam)
        if (steamIdMatch == null || steamIdMatch.groupValues.size != 2) {
            return null
        }
        val communityId = steamIdMatch.groupValues[1].toLongOrNull() ?: return null
        val url = UriBuilder.newInstance()
            .host("steamcommunity.com")
            .scheme("https")
            .path("/openid/login").apply {
                queryParam("openid.ns", "http://specs.openid.net/auth/2.0")
                for (param in params) {
                    // Sent once each, mode as check_authentication instead of the login's id_res
                    if (param.key == "openid.ns" || param.key == "openid.mode") continue
                    queryParam(param.key, param.value.firstOrNull() ?: "")
                }
                queryParam("openid.mode", "check_authentication")
            }.build()
        val response = httpClient.post(url.toURL())
        if (response.status != HttpStatusCode.OK) {
            return null
        }
        // Steam also answers forged or reused logins with 200, only "is_valid:true" means the login is genuine
        if (response.bodyAsText().lines().none { it.trim() == "is_valid:true" }) {
            return null
        }
        return communityId
    }

    private suspend fun createCookie(communityId: Long, expiry: Instant): String? {
        val user = userRepository.getByCommunityId(communityId) ?: return null

        val arr = ByteArray(32)
        secureRandom.nextBytes(arr)
        val token = HexFormat.of().formatHex(arr)
        cookieRepository.create(token, user.userId, expiry)
        return token
    }

    private suspend fun createCookieForUser(communityId: Long): NewCookie {
        val expiry = Instant.now() + Duration.of(30, ChronoUnit.DAYS)
        val cookieToken = createCookie(communityId, expiry) ?: throw UnauthorizedException()

        return NewCookie.Builder("id")
            .sameSite(NewCookie.SameSite.STRICT)
            .secure(LaunchMode.current() != LaunchMode.DEVELOPMENT)
            .httpOnly(true)
            .path("/")
            .value(cookieToken)
            .expiry(Date(expiry.toEpochMilli()))
            .build()
    }

    @GET
    @Path("/steam-callback")
    @PermitAll
    fun steamAuth(
        @Context uriInfo: UriInfo,
        @Context context: RoutingContext
    ): Uni<Response> = suspending {
        val communityId = verifySteamAuthRequest(uriInfo, steamCallbackUrl(context)) ?: throw UnauthorizedException()

        if (userRepository.getByCommunityId(communityId) == null) {
            return@suspending Response
                .status(HttpStatusCode.Found.value)
                .header(HttpHeaderNames.LOCATION.toString(), "/?no_account")
                .build()
        }

        val cookie = createCookieForUser(communityId)

        val newUri = "/account"

        Response
            .status(HttpStatusCode.Found.value)
            .header(HttpHeaderNames.LOCATION.toString(), newUri)
            .cookie(cookie)
            .build()
    }
}