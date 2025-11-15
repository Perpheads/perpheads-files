package com.perpheads.files.services

import com.perpheads.files.data.FileData
import io.vertx.core.http.HttpServerResponse
import java.io.File

interface FileBackend {
    suspend fun upload(data: FileData, file: File)
    suspend fun delete(data: FileData): Boolean
    suspend fun sendFile(data: FileData, start: Long = 0, end: Long = data.size, response: HttpServerResponse)
}