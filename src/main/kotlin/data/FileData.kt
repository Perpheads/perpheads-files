package com.perpheads.files.data

import java.time.Instant

data class FileData(
    val fileId: Int,
    val link: String,
    val filename: String,
    val mimeType: String,
    val userId: Int?,
    val uploadDate: Instant,
    val size: Long,
)