package com.perpheads.files.services

import com.perpheads.files.data.FileData
import io.vertx.mutiny.core.buffer.Buffer
import kotlinx.coroutines.flow.Flow
import java.io.File

interface FileBackend {
    suspend fun upload(data: FileData, file: File)
    suspend fun delete(data: FileData): Boolean
    fun getFileFlow(data: FileData, start: Long = 0, end: Long = data.size): Flow<Buffer>
}