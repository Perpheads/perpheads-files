package com.perpheads.files.controllers

import com.perpheads.files.auth.PhFilesCookieCredential
import com.perpheads.files.data.AccountInfoResponse
import com.perpheads.files.data.ApiKeyResponse
import com.perpheads.files.data.CreateUserRequest
import com.perpheads.files.filesUser
import com.perpheads.files.repository.CookieRepository
import com.perpheads.files.repository.UserRepository
import com.perpheads.files.suspending
import com.perpheads.files.suspendingNoContent
import io.ktor.http.HttpStatusCode
import io.netty.handler.codec.http.HttpHeaderNames
import io.quarkus.security.identity.CurrentIdentityAssociation
import io.smallrye.mutiny.Uni
import io.smallrye.mutiny.coroutines.awaitSuspending
import jakarta.annotation.security.RolesAllowed
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.NewCookie
import jakarta.ws.rs.core.Response

@Path("/api/user")
@RolesAllowed("user")
class UserController(
    private val userRepository: UserRepository,
    private val cookieRepository: CookieRepository,
) {
    @Inject
    private lateinit var securityIdentity: CurrentIdentityAssociation

    @GET
    fun getUser(): Uni<AccountInfoResponse> = suspending {
        val user = securityIdentity.filesUser()
        AccountInfoResponse(
            communityId = user.communityId,
            name = user.name,
            admin = user.admin,
            userId = user.userId,
        )
    }

    @GET
    @Path("/api-key")
    fun getApiKey(): Uni<ApiKeyResponse> = suspending {
        val user = securityIdentity.filesUser()
        val apiKey = userRepository.getById(user.userId)?.apiKey
            ?: throw NotFoundException("API key not found")
        ApiKeyResponse(apiKey)
    }


    @POST
    @Path("/api-key")
    fun regenerateApiKey(): Uni<ApiKeyResponse> = suspending {
        val user = securityIdentity.filesUser()
        val apiKey = userRepository.generateApiKey(user.userId)
        ApiKeyResponse(apiKey)
    }

    @POST
    @RolesAllowed("admin")
    @Consumes(MediaType.APPLICATION_JSON)
    fun createUser(
        request: CreateUserRequest
    ): Uni<Response> = suspendingNoContent {
        userRepository.createUser(request.communityId, request.name)
    }

    @GET
    @Path("/logout")
    fun logout(): Uni<Response> = suspending {
        val deferredIdentity = securityIdentity.deferredIdentity.awaitSuspending()
        val cookieCredential = deferredIdentity.credentials.firstOrNull() as? PhFilesCookieCredential
        if (cookieCredential != null) {
            cookieRepository.delete(cookieCredential.token)
        }
        Response
            .status(HttpStatusCode.Found.value)
            .header(HttpHeaderNames.LOCATION.toString(), "/")
            .build()
    }
}