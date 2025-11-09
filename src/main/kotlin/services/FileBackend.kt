package com.perpheads.files.services

import com.perpheads.files.data.FileData
import kotlinx.coroutines.flow.Flow
import java.io.File

interface FileBackend {
    suspend fun upload(data: FileData, file: File)
    suspend fun delete(data: FileData): Boolean
    fun getFileFlow(data: FileData): Flow<ByteArray>
}