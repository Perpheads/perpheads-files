package com.perpheads.files.controllers

import com.perpheads.files.data.FileData
import com.perpheads.files.filesUser
import com.perpheads.files.repository.FileRepository
import com.perpheads.files.services.CachedS3FileBackend
import com.perpheads.files.suspending
import com.perpheads.files.utils.alphaNumeric
import io.quarkus.security.identity.CurrentIdentityAssociation
import io.smallrye.mutiny.Uni
import jakarta.annotation.security.RolesAllowed
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.BadRequestException
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.core.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jboss.resteasy.reactive.RestForm
import org.jboss.resteasy.reactive.multipart.FileUpload
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.SecureRandom
import java.time.Instant
import javax.imageio.ImageIO
import kotlin.math.min
import kotlin.math.roundToInt

@ApplicationScoped
@RolesAllowed("uploader")
@Path("/")
class UploadController(
    private val fileRepository: FileRepository,
    private val fileBackend: CachedS3FileBackend,
) {
    companion object {
        const val THUMBNAIL_SIZE = 128
    }

    @Inject
    private lateinit var securityIdentity: CurrentIdentityAssociation

    private val secureRandom = SecureRandom()

    private fun getFileExtensionFromName(name: String): String {
        return if (name.contains(".")) {
            val suffix = name.substring(name.lastIndexOf(".") + 1)
            "." + suffix.substring(0, min(4, suffix.length))
        } else ""
    }

    private suspend fun tryGenerateThumbnail(fileId: Int, image: File, mimeType: String) {
        runCatching {
            if (mimeType != "image/png" && mimeType != "image/jpeg") return
            val image = withContext(Dispatchers.IO) {
                ImageIO.read(image)
            }
            val thumbImage = BufferedImage(THUMBNAIL_SIZE, THUMBNAIL_SIZE, BufferedImage.TYPE_INT_RGB)
            val g = thumbImage.createGraphics()

            val shorterSide = min(image.height, image.width)
            val scaleFactor = THUMBNAIL_SIZE.toDouble() / shorterSide
            val scaledWidth = (image.width * scaleFactor).roundToInt()
            val scaledHeight = (image.height * scaleFactor).roundToInt()
            val x = -(scaledWidth - THUMBNAIL_SIZE) / 2
            val y = -(scaledHeight - THUMBNAIL_SIZE) / 2
            g.background = Color.WHITE
            g.clearRect(0, 0, THUMBNAIL_SIZE, THUMBNAIL_SIZE)
            g.drawImage(image, x, y, scaledWidth, scaledHeight, Color(255, 255, 255, 0), null)

            image.flush()
            val outputArrStream = ByteArrayOutputStream()
            @Suppress("BlockingMethodInNonBlockingContext")
            ImageIO.write(thumbImage, "jpg", outputArrStream)
            thumbImage.flush()
            val thumbnail = outputArrStream.toByteArray()

            fileRepository.createThumbnail(fileId, thumbnail)
        }
    }

    private fun validateFilename(name: String): Boolean {
        return name.matches("^[-_.A-Za-z0-9 ()]+\$".toRegex())
    }

    class FileUploadResponse(
        val link: String,
    )

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Path("/upload")
    fun upload(
        @RestForm("file") fileUpload: FileUpload,
    ): Uni<FileUploadResponse> = suspending {
        val user = securityIdentity.filesUser()
        val file = fileUpload.uploadedFile().toFile()
        val filename = fileUpload.fileName()
        if (!validateFilename(filename)) {
            throw BadRequestException()
        }
        val link = secureRandom.alphaNumeric(16) + getFileExtensionFromName(filename)
        val mimeType = fileUpload.contentType() ?: MediaType.APPLICATION_OCTET_STREAM
        val fileData = fileRepository.create(
            FileData(
                fileId = -1,
                link = link,
                filename = filename,
                mimeType = mimeType,
                userId = user.userId,
                uploadDate = Instant.now(),
                size = file.length(),
            )
        )
        fileBackend.upload(fileData, file)
        tryGenerateThumbnail(fileData.fileId, file, mimeType)
        fileRepository.markFileAsUploaded(fileData.fileId)
        FileUploadResponse(link = fileData.link)
    }
}