package com.perpheads.files.controllers

import com.perpheads.files.arcCoroutineScope
import com.perpheads.files.arcDispatcher
import com.perpheads.files.data.FileData
import com.perpheads.files.repository.FileRepository
import com.perpheads.files.services.CachedS3FileBackend
import com.perpheads.files.services.FileBackend
import com.perpheads.files.suspending
import io.quarkus.arc.Arc
import io.quarkus.scheduler.kotlin.runtime.ApplicationCoroutineScope
import io.quarkus.scheduler.kotlin.runtime.VertxDispatcher
import io.smallrye.mutiny.Multi
import io.smallrye.mutiny.Uni
import io.vertx.core.Vertx
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.GET
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.CacheControl
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import org.jboss.resteasy.reactive.RestMulti
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import kotlin.coroutines.cancellation.CancellationException

@ApplicationScoped
@Path("/")
class FileDownloadController(
    private val fileRepository: FileRepository,
    private val fileBackend: CachedS3FileBackend,
) {
    private class NonPropagatingCancellationException : CancellationException()

    val whitelistedTypes = setOf(
        "image/png",
        "image/jpeg",
        "image/gif",
        "image/webp",
        "video/mp4",
        "video/ogg",
        "video/mpeg",
        "video/webm",
        "text/plain",
        "application/json",
        "audio/mp4",
        "audio/ogg",
        "audio/mpeg",
        "audio/mp3",
        "application/pdf",
    )

    val forcedTextTypes = setOf(
        "application/json",
        "application/xml",
    )

    @GET
    @Path("/{link:\\w{16}(?:\\.\\w{1,4})?}")
    fun downloadFile(link: String): RestMulti<ByteArray> {
        class DownloadData(val fileData: FileData, val downloadStream: Flow<ByteArray>)

        return RestMulti.fromUniResponse(suspending {
            val fileData = fileRepository.findByLink(link) ?: throw NotFoundException("")
            DownloadData(fileData, fileBackend.getFileFlow(fileData))
        }, { data ->
            Multi.createFrom().emitter { em ->
                val job = arcCoroutineScope.launch(arcDispatcher) {
                    try {
                        data.downloadStream.collect { item ->
                            if (em.isCancelled) {
                                throw NonPropagatingCancellationException()
                            }
                            em.emit(item)
                        }
                        em.complete()
                    } catch (th: Throwable) {
                        when (th) {
                            is NonPropagatingCancellationException -> em.complete()
                            else -> em.fail(th)
                        }
                    }
                }
                em.onTermination {
                    job.cancel(NonPropagatingCancellationException())
                }
            }
        }, { data ->
            val storedContentType = data.fileData.mimeType
            val contentType = if (whitelistedTypes.contains(storedContentType)) {
                storedContentType
            } else if (forcedTextTypes.contains(storedContentType) || storedContentType.contains("text/any")) {
                "text/plain"
            } else {
                "application/octet-stream"
            }

            mutableMapOf(
                "Content-Length" to listOf(data.fileData.size.toString()),
                "Content-Type" to listOf(contentType),
                "Cache-Control" to listOf("public, max-age=${Duration.ofDays(30).seconds.toInt()}, immutable"),
                "Content-Disposition" to listOf(
                    "inline; filename*=UTF-8''${
                        URLEncoder.encode(
                            data.fileData.filename,
                            StandardCharsets.UTF_8
                        )
                    }"
                ),
            )
        })
    }

    @GET
    @Path("/thumbnail/{link:\\w{16}(?:\\.\\w{1,4})?}")
    @Produces("image/jpeg")
    fun getThumbnail(link: String): Uni<Response> = suspending {
        val file = fileRepository.getThumbnail(link) ?: throw NotFoundException()
        Response.ok(file)
            .header("Cache-Control", "public, max-age=${Duration.ofDays(30).seconds.toInt()}, immutable")
            .build()
    }
}