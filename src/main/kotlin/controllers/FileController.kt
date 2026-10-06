package com.perpheads.files.controllers

import com.perpheads.files.data.FileOverallStatistics
import com.perpheads.files.data.FileUserStatistics
import com.perpheads.files.data.ListFilesResponse
import com.perpheads.files.data.SearchRequest
import com.perpheads.files.filesUser
import com.perpheads.files.repository.FileRepository
import com.perpheads.files.services.CachedS3FileBackend
import com.perpheads.files.suspending
import com.perpheads.files.suspendingNoContent
import io.quarkus.security.identity.CurrentIdentityAssociation
import io.smallrye.mutiny.Uni
import jakarta.annotation.security.RolesAllowed
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.Response
import org.jboss.logging.Logger
import kotlin.math.ceil
import kotlin.math.max

@ApplicationScoped
@Path("/api/file")
@RolesAllowed("user")
class FileController(
    private val fileRepository: FileRepository,
    private val fileBackend: CachedS3FileBackend,
) {
    companion object {
        private val LOG = Logger.getLogger(FileController::class.java.name)
    }

    @Inject
    private lateinit var securityIdentity: CurrentIdentityAssociation

    @POST
    fun listFiles(
        searchRequest: SearchRequest,
    ): Uni<ListFilesResponse> = suspending {
        val user = securityIdentity.filesUser()
        val page = (searchRequest.page ?: 1).coerceAtLeast(1)
        val entriesPerPage = searchRequest.entriesPerPage.coerceIn(1, 100)
        val query = searchRequest.query.takeIf { it.isNotBlank() }

        val (fileCount, files) = fileRepository.findFiles(
            userId = user.userId,
            beforeId = searchRequest.beforeId,
            offset = (page - 1) * entriesPerPage,
            limit = entriesPerPage,
            searchStr = query,
        )
        val totalPages = max(ceil(fileCount / entriesPerPage.toDouble()), 1.0).toInt()

        ListFilesResponse(
            totalPages = totalPages,
            currentPage = page,
            files = files,
        )
    }

    @DELETE
    @Path("/{id}")
    fun deleteFile(
        @PathParam("id") id: Int,
    ): Uni<Response> = suspendingNoContent {
        val user = securityIdentity.filesUser()
        val file = fileRepository.findById(id)
            ?.takeIf { it.userId == user.userId }
            ?: throw NotFoundException()

        fileBackend.delete(file)
        fileRepository.delete(file.fileId)
    }

    private fun validateFilename(name: String): Boolean {
        return name.matches("^[-_.A-Za-z0-9 ()]{1,200}$".toRegex())
    }

    @PUT
    @Path("/{id}/filename")
    fun renameFile(
        @PathParam("id") id: Int,
        @QueryParam("filename") filename: String,
    ): Uni<Response> = suspendingNoContent {
        if (!validateFilename(filename)) {
            throw BadRequestException()
        }

        val user = securityIdentity.filesUser()
        val file = fileRepository.findById(id)
            ?.takeIf { it.userId == user.userId }
            ?: throw NotFoundException()

        fileRepository.rename(file.fileId, filename)
    }

    class NewLinkResponse(val link: String)

    /** A new link for one of the user's files; the old link stops working */
    @POST
    @Path("/{id}/link")
    fun regenerateLink(
        @PathParam("id") id: Int,
    ): Uni<NewLinkResponse> = suspending {
        val user = securityIdentity.filesUser()
        val file = fileRepository.findById(id)
            ?.takeIf { it.userId == user.userId }
            ?: throw NotFoundException()

        NewLinkResponse(fileRepository.regenerateLink(file))
    }

    class NewLinksResponse(val fileCount: Int)

    /** New links for all files of all users; every link shared so far stops working */
    @POST
    @Path("/regenerate-all-links")
    @RolesAllowed("admin")
    fun regenerateAllLinks(): Uni<NewLinksResponse> = suspending {
        val user = securityIdentity.filesUser()
        val fileCount = fileRepository.regenerateAllLinks()
        LOG.info("${user.name} gave all $fileCount files new links")
        NewLinksResponse(fileCount)
    }

    class FileStatisticsResponse(
        val userStatistics: List<FileUserStatistics>,
        val overallStatistics: FileOverallStatistics
    )

    @GET
    @Path("/statistics")
    @RolesAllowed("admin")
    fun getStatistics(): Uni<FileStatisticsResponse> = suspending {
        val userStatistics = fileRepository.getUserStatistics()
        val overallStatistics = fileRepository.getOverallStatistics()

        FileStatisticsResponse(userStatistics, overallStatistics)
    }
}