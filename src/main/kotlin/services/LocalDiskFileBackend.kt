package com.perpheads.files.services

import com.perpheads.files.data.FileData
import io.quarkus.runtime.StartupEvent
import io.vertx.core.http.HttpServerResponse
import io.vertx.mutiny.core.Vertx
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import jakarta.ws.rs.NotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.io.File
import java.nio.file.Path
import kotlin.io.path.*

@ApplicationScoped
class LocalDiskFileBackend(
    @param:ConfigProperty(name = "file.backend.disk.storage_path")
    val storagePath: Path,
    private val vertx: Vertx
) : FileBackend {
    private fun FileData.getTargetPath(): Path {
        return storagePath / link
    }

    override suspend fun upload(data: FileData, file: File) {
        withContext(Dispatchers.IO) {
            file.copyTo(data.getTargetPath().toFile())
        }
    }

    override suspend fun delete(data: FileData): Boolean {
        return withContext(Dispatchers.IO) {
            data.getTargetPath().toFile().delete()
        }
    }

    suspend fun storeFromFlow(data: FileData, flow: Flow<ByteArray>) {
        withContext(Dispatchers.IO) {
            data.getTargetPath().outputStream().use { outputStream ->
                flow.collect {
                    @Suppress("BlockingMethodInNonBlockingContext")
                    outputStream.write(it)
                }
            }
        }
    }

    suspend fun listFiles(): List<File> {
        return withContext(Dispatchers.IO) {
            storagePath.toFile().listFiles().toList().filter { it.isFile }
        }
    }

    override suspend fun sendFile(data: FileData, start: Long, end: Long, response: HttpServerResponse) {
        val file = data.getTargetPath()
        if (!file.exists()) throw NotFoundException("File not found: ${data.filename}")

        response.sendFile(file.absolutePathString(), start, end - start)
            .toCompletionStage()
            .await()
    }

    fun onStart(@Observes startupEvent: StartupEvent) {
        if (!storagePath.exists()) {
            storagePath.createDirectory()
        }
    }
}