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
import io.vertx.mutiny.core.buffer.Buffer
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.GET
import jakarta.ws.rs.HEAD
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
import org.jboss.resteasy.reactive.RestHeader
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

    private fun FileData.getContentDisposition(): String {
        return "inline; filename*=UTF-8''${URLEncoder.encode(filename, StandardCharsets.UTF_8)}"
    }

    private fun FileData.getSafeContentType(): String {
        return if (whitelistedTypes.contains(mimeType)) {
            mimeType
        } else if (forcedTextTypes.contains(mimeType) || mimeType.contains("text/any")) {
            "text/plain"
        } else {
            "application/octet-stream"
        }
    }

    private fun getCacheHeader(): String =
        "public, max-age=${Duration.ofDays(30).seconds.toInt()}, immutable"

    @HEAD
    @Path("/{link:\\w{16}(?:\\.\\w{1,4})?}")
    fun getFileInformation(link: String): Uni<Response> = suspending {
        println("GETTING FILE INFO")
        val fileData = fileRepository.findByLink(link) ?: throw NotFoundException()

        Response.ok()
            .header("Cache-Control", getCacheHeader())
            .header("Content-Disposition", fileData.getContentDisposition())
            .header("Content-Type", fileData.getSafeContentType())
            .header("Content-Length", fileData.size.toString())
            .header("Accept-Ranges", "bytes")
            .build()
    }

    private val rangeRegex = Regex("bytes=(\\d*)-?(\\d*)")

    private fun FileData.getRangeHeaderRange(rangeHeader: String?): LongRange? {
        if (rangeHeader == null) return null
        val match = rangeRegex.matchEntire(rangeHeader) ?: return null
        val start = match.groupValues[1].toLongOrNull()
        val end = match.groupValues[2].toLongOrNull()
        if (start == null && end == null) return null

        return (start ?: 0) .. (end ?: size)
    }

    @GET
    @Path("/{link:\\w{16}(?:\\.\\w{1,4})?}")
    fun downloadFile(
        link: String,
        @RestHeader("Range")
        rangeHeader: String?,
    ): RestMulti<Buffer> {
        class DownloadData(val fileData: FileData, val downloadStream: Flow<Buffer>, val range: LongRange?)

        return RestMulti.fromUniResponse(suspending {
            val fileData = fileRepository.findByLink(link) ?: throw NotFoundException()
            val range = fileData.getRangeHeaderRange(rangeHeader)
            DownloadData(fileData, fileBackend.getFileFlow(fileData, range?.start ?: 0L, (range?.endInclusive ?: fileData.size)), range)
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
            mutableMapOf(
                "Content-Length" to listOf(data.fileData.size.toString()),
                "Content-Type" to listOf(data.fileData.getSafeContentType()),
                "Accept-Ranges" to listOf("bytes"),
                "Cache-Control" to listOf(getCacheHeader()),
                "Content-Disposition" to listOf(data.fileData.getContentDisposition()),
            )
        }, { data ->
            if (data.range != null) 206 else 200
        })
    }

    @GET
    @Path("/thumbnail/{link:\\w{16}(?:\\.\\w{1,4})?}")
    @Produces("image/jpeg")
    fun getThumbnail(link: String): Uni<Response> = suspending {
        val file = fileRepository.getThumbnail(link) ?: throw NotFoundException()
        Response.ok(file)
            .header("Cache-Control", getCacheHeader())
            .build()
    }
}