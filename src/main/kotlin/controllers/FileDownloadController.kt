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
        "public, max-age=${Duration.ofDays(30).seconds.toInt()}, immutable"

    @HEAD
    @Path("/{link:\\w{16}(?:\\.\\w{1,4})?}")
    fun getFileInformation(link: String): Uni<Response> = suspending {
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
        response: HttpServerResponse,
    ): Uni<Void?> = suspendingVoid {
        val fileData = fileRepository.findByLink(link) ?: throw NotFoundException()
        val range = fileData.getRangeHeaderRange(rangeHeader)

        response.putHeader("Content-Length", fileData.size.toString())
        response.putHeader("Content-Type", fileData.getSafeContentType())
        rangeHeader?.let {
            response.putHeader("Content-Range", rangeHeader)
        }
        response.putHeader("Accept-Ranges", "bytes")
        response.putHeader("Cache-Control", getCacheHeader())
        response.putHeader("Content-Disposition", fileData.getContentDisposition())

        if (rangeHeader != null) {
            response.statusCode = 206
        } else {
            response.statusCode = 200
        }

        fileBackend.sendFile(fileData, range?.start ?: 0L, (range?.endInclusive ?: fileData.size), response)
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