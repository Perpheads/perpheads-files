package com.perpheads.files

import io.quarkus.security.identity.SecurityIdentity
import io.vertx.core.http.HttpServerRequest
import jakarta.inject.Inject
import jakarta.ws.rs.container.*
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.UriInfo
import jakarta.ws.rs.ext.Provider
import org.jboss.logging.Logger
import java.time.Duration
import java.time.Instant


@Provider
@PreMatching
class LoggingPreFilter : ContainerRequestFilter {

    @Context
    lateinit var info: UriInfo

    @Context
    lateinit var request: HttpServerRequest

    override fun filter(context: ContainerRequestContext) {
        context.setProperty("start_time", Instant.now())
    }
}


@Provider
class LoggingPostFilter : ContainerResponseFilter {
    companion object {
        private val LOG: Logger = Logger.getLogger(LoggingPreFilter::class.java)
    }

    @Context
    lateinit var info: UriInfo

    @Context
    lateinit var request: HttpServerRequest

    @Inject
    lateinit var securityIdentity: SecurityIdentity

    override fun filter(requestContext: ContainerRequestContext, responseContext: ContainerResponseContext) {
        val startTime = requestContext.getProperty("start_time") as? Instant ?: Instant.now()
        val method = requestContext.method
        val query = info.requestUri.query ?: ""
        val queryStr = if (query.isNotEmpty()) "?$query" else ""
        val path = info.requestUri.path + queryStr
        val address = request.remoteAddress().toString()
        val millis = Duration.between(startTime, Instant.now()).toMillis()

        val user = runCatching {
            (securityIdentity.principal?.name?.takeIf { it.isNotBlank() })
        }.getOrNull() ?: "anonymous"

        LOG.infof("%s %s - from IP %s - status %d - user: %s - took %sms", method, path, address, responseContext.status, user, millis)
    }
}