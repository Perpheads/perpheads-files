package com.perpheads.files.auth

import io.quarkus.security.identity.IdentityProviderManager
import io.quarkus.security.identity.SecurityIdentity
import io.quarkus.security.identity.request.AuthenticationRequest
import io.quarkus.vertx.http.runtime.security.ChallengeData
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism
import io.smallrye.mutiny.Uni
import io.vertx.ext.web.RoutingContext
import jakarta.enterprise.context.ApplicationScoped
import java.util.Optional

@ApplicationScoped
class PhFilesHttpAuthMechanism : HttpAuthenticationMechanism {
    override fun authenticate(
        context: RoutingContext,
        identityProviderManager: IdentityProviderManager
    ): Uni<SecurityIdentity> {
        val cookie = context.request().cookies("id").firstOrNull()
        val apiKey = context.request().headers()["API-KEY"]

        return if (cookie != null) {
            val credentials = PhFilesCookieCredential(cookie.value)
            identityProviderManager.authenticate(PhFilesCookieAuthRequest(credentials))
        } else if (apiKey != null) {
            val credentials = PhFilesApiKeyCredentials(apiKey)
            identityProviderManager.authenticate(PhFilesApiKeyAuthRequest(credentials))
        } else {
            Uni.createFrom().optional(Optional.empty())
        }
    }

    override fun getChallenge(context: RoutingContext): Uni<ChallengeData> {
        context.response().removeCookie("id")
        return Uni.createFrom().optional(Optional.empty())
    }

    override fun getCredentialTypes(): MutableSet<Class<out AuthenticationRequest>> {
        return mutableSetOf(PhFilesCookieAuthRequest::class.java, PhFilesApiKeyAuthRequest::class.java)
    }
}