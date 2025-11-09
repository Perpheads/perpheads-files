package com.perpheads.files

import com.perpheads.files.auth.SteamUserPrincipal
import com.perpheads.files.data.AccountInfoResponse
import io.quarkus.arc.Arc
import io.quarkus.scheduler.kotlin.runtime.ApplicationCoroutineScope
import io.quarkus.scheduler.kotlin.runtime.VertxDispatcher
import io.quarkus.security.UnauthorizedException
import io.quarkus.security.identity.CurrentIdentityAssociation
import io.quarkus.security.identity.SecurityIdentity
import io.smallrye.mutiny.Uni
import io.smallrye.mutiny.coroutines.asUni
import io.smallrye.mutiny.coroutines.awaitSuspending
import io.vertx.core.Vertx
import jakarta.ws.rs.core.Response
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async


suspend fun CurrentIdentityAssociation.filesUser(): AccountInfoResponse {
    return deferredIdentity.awaitSuspending().filesUser
}

val SecurityIdentity.filesUser: AccountInfoResponse
    get() = (principal as? SteamUserPrincipal)?.account ?: throw UnauthorizedException()


val arcCoroutineScope: CoroutineScope
    get() = Arc.container().instance(ApplicationCoroutineScope::class.java).get()

val arcDispatcher: CoroutineDispatcher
    get() = Vertx.currentContext()?.let(::VertxDispatcher)
        ?: throw IllegalStateException("No Vertx context found")

@OptIn(ExperimentalCoroutinesApi::class)
fun <R> suspending(block: suspend () -> R): Uni<R> {
    val uni = arcCoroutineScope.async(arcDispatcher) {
        block()
    }.asUni()
    return uni
}

fun suspendingVoid(block: suspend () -> Unit): Uni<Void?> = suspending {
    block()
    null
}

fun suspendingNoContent(block: suspend () -> Unit): Uni<Response> {
    return suspending {
        block()
        Response.noContent().build()
    }
}