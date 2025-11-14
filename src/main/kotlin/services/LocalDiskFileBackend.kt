package com.perpheads.files.services

import com.perpheads.files.data.FileData
import io.quarkus.runtime.StartupEvent
import io.smallrye.mutiny.coroutines.asFlow
import io.smallrye.mutiny.coroutines.awaitSuspending
import io.vertx.core.file.OpenOptions
import io.vertx.mutiny.core.Vertx
import io.vertx.mutiny.core.buffer.Buffer
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import jakarta.ws.rs.NotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.outputStream

@ApplicationScoped
class LocalDiskFileBackend(
    @param:ConfigProperty(name = "file.backend.disk.storage_path")
    val storagePath: Path,
    private val vertx: Vertx
) : FileBackend {
    companion object {
        const val BUFFER_SIZE = 32768
    }

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

    override fun getFileFlow(data: FileData, start: Long, end: Long): Flow<Buffer> = flow {
        val file = data.getTargetPath().toFile()
        if (!file.exists()) throw NotFoundException("File not found: ${data.filename}")

        val asyncFile = vertx.fileSystem()
            .open(data.getTargetPath().toString(), OpenOptions().setRead(true))
            .awaitSuspending()

        val bufferFlow = asyncFile
            .setReadPos(start)
            .setReadLength(end - start)
            .toMulti()
            .onTermination().invoke { asyncFile.close() }
            .asFlow()

        emitAll(bufferFlow)
    }

    fun onStart(@Observes startupEvent: StartupEvent) {
        if (!storagePath.exists()) {
            storagePath.createDirectory()
        }
    }
}