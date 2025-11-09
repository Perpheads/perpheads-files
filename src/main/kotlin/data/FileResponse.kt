package com.perpheads.files.data

import java.time.Instant

class ListFilesResponse(
    val totalPages: Int,
    val currentPage: Int,
    val files: List<FileResponse>,
)

data class FileResponse(
    val fileId: Int,
    val link: String,
    val filename: String,
    val mimeType: String,
    val uploadDate: Instant,
    val size: Int,
    val thumbnail: String?,
    val hasThumbnail: Boolean
)