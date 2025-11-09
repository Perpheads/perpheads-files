package com.perpheads.files.auth

import com.perpheads.files.data.toAccountInfoResponse
import com.perpheads.files.repository.CookieRepository
import com.perpheads.files.suspending
import io.quarkus.security.identity.AuthenticationRequestContext
import io.quarkus.security.identity.IdentityProvider
import io.quarkus.security.identity.SecurityIdentity
import io.quarkus.security.runtime.QuarkusSecurityIdentity
import io.smallrye.mutiny.Uni
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class PhFilesCookieAuthIdentityProvider(
    private val cookieRepository: CookieRepository,
): IdentityProvider<PhFilesCookieAuthRequest> {
    override fun getRequestType(): Class<PhFilesCookieAuthRequest> = PhFilesCookieAuthRequest::class.java

    override fun authenticate(
        request: PhFilesCookieAuthRequest,
        context: AuthenticationRequestContext
    ): Uni<SecurityIdentity?> = suspending {
        val user = cookieRepository.getUserForCookie(request.token.token) ?: return@suspending null

        val builder = QuarkusSecurityIdentity.builder()
            .addCredential(request.token)
            .setPrincipal(SteamUserPrincipal(user.toAccountInfoResponse()))
            .addRole("user")
            .addRole("uploader")

        if (user.admin) {
            builder.addRole("admin")
        }

        builder.build()
    }
}