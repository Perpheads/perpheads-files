package com.perpheads.files.controllers

import com.perpheads.files.data.FileData
import com.perpheads.files.repository.FileRepository
import com.perpheads.files.services.CachedS3FileBackend
import com.perpheads.files.suspending
import com.perpheads.files.suspendingVoid
import io.smallrye.mutiny.Uni
import io.vertx.core.http.HttpServerResponse
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.*
import jakarta.ws.rs.core.Response
import org.jboss.resteasy.reactive.RestHeader
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration

@ApplicationScoped
@Path("/")
class FileDownloadController(
    private val fileRepository: FileRepository,
    private val fileBackend: CachedS3FileBackend,
) {
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
        "private, max-age=${Duration.ofDays(30).seconds.toInt()}, immutable"

    private fun FileData.getHeaders(rangeHeader: String?): Map<String, String> = buildMap {
        val range = getRangeHeaderRange(rangeHeader)
        put("Content-Length", range?.contentLength()?.toString() ?: this@getHeaders.size.toString())
        put("Content-Type", getSafeContentType())
        range?.let {
            put("Content-Range", range.toHeader())
        }
        put("Accept-Ranges", "bytes")
        put("Cache-Control", getCacheHeader())
        put("Content-Disposition", getContentDisposition())

    }

    @HEAD
    @Path("/{link:\\w{16}(?:\\.\\w{1,4})?}")
    fun getFileInformation(
        link: String,
        @RestHeader("Range")
        rangeHeader: String?,
    ): Uni<Response> = suspending {
        val fileData = fileRepository.findByLink(link) ?: throw NotFoundException()

        val rangeData = fileData.getRangeHeaderRange(rangeHeader)
        val status = if (rangeData != null) 206 else 200

        Response.status(status).apply {
            fileData.getHeaders(rangeHeader).forEach {
                header(it.key, it.value)
            }
        }.build()
    }

    private val rangeRegex = Regex("bytes=(\\d*)-?(\\d*)")

    private class RangeHeaderData(private val start: Long?, private val end: Long?, private val fileData: FileData) {
        fun isValid(): Boolean {
            return (start == null || start in 0..<fileData.size) &&
                    (end == null || end in 0..<fileData.size) &&
                    (start == null || end == null || start <= end)
        }

        fun start(): Long = start ?: 0

        fun end(): Long = end ?: (fileData.size - 1)

        fun endExclusive(): Long = end?.let { it + 1 } ?: fileData.size

        fun contentLength(): Long {
            return endExclusive() - start()
        }

        fun toHeader(): String {
            return "bytes ${start()}-${end()}/${fileData.size}"
        }
    }

    private fun FileData.getRangeHeaderRange(rangeHeader: String?): RangeHeaderData? {
        if (rangeHeader == null) return null
        val match = rangeRegex.matchEntire(rangeHeader) ?: return null
        val start = match.groupValues[1].toLongOrNull()
        val end = match.groupValues[2].toLongOrNull()
        if (start == null && end == null) return null
        val data = RangeHeaderData(start, end, this)

        return data.takeIf { it.isValid() }
    }

    @GET
    @Path("/{link:\\w{16}(?:\\.\\w{1,4})?}")
    fun downloadFile(
        link: String,
        @RestHeader("Range")
        rangeHeader: String?,
        response: HttpServerResponse,
    ): Uni<Void?> = suspendingVoid {
        val fileData = fileRepository.findByLink(link) ?: throw NotFoundException()
        val range = fileData.getRangeHeaderRange(rangeHeader)

        fileData.getHeaders(rangeHeader).forEach {
            response.putHeader(it.key, it.value)
        }

        if (range != null) {
            response.statusCode = 206
        } else {
            response.statusCode = 200
        }

        fileBackend.sendFile(fileData, range?.start() ?: 0L, range?.endExclusive() ?: fileData.size, response)
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