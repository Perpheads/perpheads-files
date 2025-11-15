package com.perpheads.files.controllers

import com.perpheads.files.arcCoroutineScope
import com.perpheads.files.arcDispatcher
import com.perpheads.files.services.ShareService
import io.ktor.http.*
import io.smallrye.mutiny.Multi
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.GET
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.core.MediaType
import kotlinx.coroutines.launch
import org.jboss.resteasy.reactive.RestMulti
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.coroutines.cancellation.CancellationException

@ApplicationScoped
@Path("/api/share")
class ShareController(
    private val shareService: ShareService
) {

    class ShareFileResponse(val filename: String, val size: Long)

    @GET
    @Path("/{token}")
    fun getShare(
        @PathParam("token") token: String,
    ): ShareFileResponse {
        val shareData = shareService.getShareInformation(token) ?: throw NotFoundException()
        return ShareFileResponse(shareData.filename, shareData.size)
    }

    private class NonPropagatingCancellationException : CancellationException()
    @GET
    @Path("/{token}/download")
    fun downloadSharedFile(
        @PathParam("token") token: String,
    ) : RestMulti<ByteArray> {
        val shareData = shareService.getShareInformation(token) ?: throw NotFoundException("")

        val multi = Multi.createFrom().emitter { em ->
            val job = arcCoroutineScope.launch(arcDispatcher) {
                try {
                    shareService.startDownload(token).collect { item ->
                        if (em.isCancelled) {
                            throw NonPropagatingCancellationException()
                        }
                        em.emit(item)
                    }
                    em.complete()
                } catch (th: Throwable) {
                    when (th) {
                        is NonPropagatingCancellationException -> em.complete()
                        else -> {
                            em.fail(th)
                        }
                    }
                }
            }
            em.onTermination {
                job.cancel(NonPropagatingCancellationException())
            }
        }

        return RestMulti.fromMultiData(multi)
            .header(HttpHeaders.ContentType, MediaType.APPLICATION_OCTET_STREAM)
            .header(HttpHeaders.ContentLength, shareData.size.toString())
            .header(HttpHeaders.ContentDisposition, "attachment; filename*=UTF-8''${
                URLEncoder.encode(
                    shareData.filename,
                    StandardCharsets.UTF_8
                )
            }")
            .build()
    }
}