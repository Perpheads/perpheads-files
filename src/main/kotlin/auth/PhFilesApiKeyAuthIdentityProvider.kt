package com.perpheads.files.auth

import com.perpheads.files.data.toAccountInfoResponse
import com.perpheads.files.repository.UserRepository
import com.perpheads.files.suspending
import io.quarkus.security.identity.AuthenticationRequestContext
import io.quarkus.security.identity.IdentityProvider
import io.quarkus.security.identity.SecurityIdentity
import io.quarkus.security.runtime.QuarkusSecurityIdentity
import io.smallrye.mutiny.Uni
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class PhFilesApiKeyAuthIdentityProvider(
    private val userRepository: UserRepository
): IdentityProvider<PhFilesApiKeyAuthRequest> {
    override fun getRequestType(): Class<PhFilesApiKeyAuthRequest> = PhFilesApiKeyAuthRequest::class.java

    override fun authenticate(
        request: PhFilesApiKeyAuthRequest,
        context: AuthenticationRequestContext
    ): Uni<SecurityIdentity?> = suspending {
        val user = userRepository.getByApiKey(request.token.token) ?: return@suspending null

        QuarkusSecurityIdentity.builder()
            .addCredential(request.token)
            .setPrincipal(SteamUserPrincipal(user.toAccountInfoResponse()))
            .addRole("uploader")
            .build()
    }
}