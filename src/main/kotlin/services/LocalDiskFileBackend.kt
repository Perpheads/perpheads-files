package com.perpheads.files.services

import com.perpheads.files.data.FileData
import com.perpheads.files.repository.FileCacheRepository
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import jakarta.ws.rs.NotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.outputStream

@ApplicationScoped
class LocalDiskFileBackend(
    @param:ConfigProperty(name = "file.backend.disk.storage_path")
    val storagePath: Path
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

    override fun getFileFlow(data: FileData): Flow<ByteArray> = flow {
        val file = data.getTargetPath().toFile()
        if (!file.exists()) throw NotFoundException("File not found: ${data.filename}")

        val buffer = ByteArray(8192)
        file.inputStream().use {
            while (true) {
                val read = withContext(Dispatchers.IO) {
                    it.read(buffer)
                }
                if (read == -1) break
                emit(buffer.copyOf(read))
            }
        }
    }
    fun onStart(@Observes startupEvent: StartupEvent) {
        if (!storagePath.exists()) {
            storagePath.createDirectory()
        }
    }
}