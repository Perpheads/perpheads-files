package com.perpheads.files.services

import com.perpheads.files.data.FileData
import com.perpheads.files.repository.FileCacheRepository
import com.perpheads.files.suspendingVoid
import io.quarkus.runtime.ShutdownEvent
import io.quarkus.runtime.StartupEvent
import io.quarkus.scheduler.Scheduled
import io.smallrye.mutiny.Uni
import io.vertx.mutiny.core.buffer.Buffer
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import jakarta.ws.rs.InternalServerErrorException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap


@ApplicationScoped
class CachedS3FileBackend(
    private val diskBackend: LocalDiskFileBackend,
    private val s3ClientService: S3ClientService,
    private val fileCacheRepository: FileCacheRepository,
    @param:ConfigProperty(name = "file.backend.cached.file_count_threshold")
    private val fileCountThreshold: Int,
    @param:ConfigProperty(name = "file.backend.cached.file_count_target")
    private val fileCountTarget: Int,
    @param:ConfigProperty(name = "file.backend.cached.file_size_threshold")
    private val fileSizeThreshold: Long,
    @param:ConfigProperty(name = "file.backend.cached.file_size_target")
    private val fileSizeTarget: Long,
) : FileBackend {

    private val fileCoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * States are defined to form a semilattice of the life of a cache entry.
     * (It is almost a linear order, but [ERROR] and [IN_CACHE] are on the same level in the hierarchy).
     * Transitions from states should always lead to a state later in the list, with the eventual maximum being [EVICTED].
     */
    enum class FileState {
        INITIALIZED, DOWNLOADING, IN_CACHE, ERROR, EVICTING, EVICTING_DOWNLOADS_COMPLETED, EVICTED
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(CachedS3FileBackend::class.java)
    }

    private inner class Entry(
        val fileData: FileData,
        var lastUsed: Instant,
        initialState: FileState = FileState.INITIALIZED,
    ) : Comparable<Entry> {
        val mutex: Mutex = Mutex()
        override fun compareTo(other: Entry): Int {
            return lastUsed.compareTo(other.lastUsed)
        }

        var state: FileState = initialState
            private set

        private val waitingCallbacks: MutableList<CompletableDeferred<Unit>> = mutableListOf()

        suspend inline fun <T> runWithLock(f: Entry.() -> T): T {
            return mutex.withLock {
                f()
            }
        }

        fun removeFromCache() {
            currentCache.remove(fileData.fileId, this)
        }

        /**
         * Must be called with the [mutex] locked!
         */
        fun changeState(newState: FileState) {
            if (newState <= state) {
                LOG.error("Changing state ${state.name} to smaller ${newState.name}. This should never happen!")
            }
            state = newState
            waitingCallbacks.forEach { it.complete(Unit) }
            waitingCallbacks.clear()
        }

        suspend fun waitForStateChange(currentState: FileState) {
            while (true) {
                val completable = runWithLock {
                    // The state has changed
                    if (state != currentState) return
                    val completable = CompletableDeferred<Unit>()
                    waitingCallbacks.add(completable)
                    completable
                }
                completable.await()
            }
        }

        var readCount: Int = 0
    }

    private val currentCache: ConcurrentHashMap<Int, Entry> = ConcurrentHashMap()

    private class EntryAccess(val entry: Entry, val stateWhenAccessed: FileState)

    private suspend inline fun getOrCreateEntryAndRunWithState(fileData: FileData, f: Entry.() -> Unit): EntryAccess {
        val computedEntry = currentCache.computeIfAbsent(fileData.fileId) {
            Entry(fileData, Instant.now())
        }
        val stateWhenAccessed = computedEntry.runWithLock {
            val stateWhenAccessed = state
            f()
            stateWhenAccessed
        }
        return EntryAccess(computedEntry, stateWhenAccessed)
    }

    private suspend fun doEviction(fileData: FileData) {
        while (true) {
            val entryAccess = getOrCreateEntryAndRunWithState(fileData) {
                when (state) {
                    FileState.INITIALIZED -> {
                        // We just created this entry, so we can just mark it as evicted immediately
                        LOG.info("Attempting to evict ${fileData.link} but it was not present")
                        changeState(FileState.EVICTED)
                        this.removeFromCache()
                        return
                    }

                    FileState.EVICTED -> {
                        LOG.info("Attempting to evict ${fileData.link} but it was already evicted by someone else")
                        // Someone else already finished evicting, we are done
                        this.removeFromCache()
                        return
                    }

                    FileState.DOWNLOADING -> {
                        // Waiting for download to finish
                    }

                    FileState.ERROR, FileState.IN_CACHE, FileState.EVICTING, FileState.EVICTING_DOWNLOADS_COMPLETED -> {
                        if (readCount == 0) {
                            LOG.info("File ${fileData.link} is not being read anymore so it can be safely evicted")
                            // No readers so we can safely delete the file.
                            changeState(FileState.EVICTED)
                            try {
                                diskBackend.delete(fileData)
                                fileCacheRepository.removeFilesFromCache(listOf(fileData.fileId))
                            } finally {
                                this.removeFromCache()
                            }
                            return
                        } else if (state != FileState.EVICTING) {
                            LOG.debug(
                                "Attempting to evict file {} but it is in state {}, marking as evicting.",
                                fileData.link,
                                state
                            )
                            changeState(FileState.EVICTING)
                        }
                    }
                }
            }
            entryAccess.entry.waitForStateChange(entryAccess.stateWhenAccessed)
        }
    }

    internal suspend fun evictFromCache(fileData: FileData) {
        // Running in a separate scope that will not be canceled to avoid cancelation exceptions that could break
        // everything
        fileCoroutineScope.launch {
            doEviction(fileData)
        }.join()
    }

    override suspend fun delete(data: FileData): Boolean {
        val success = s3ClientService.deleteFile(data.link)
        evictFromCache(data)
        return success
    }

    override suspend fun upload(data: FileData, file: File) {
        s3ClientService.uploadFile(file, data.link, data.mimeType)
        diskBackend.upload(data, file) // Immediately make it available
        fileCacheRepository.addFileToCache(data.fileId)
        currentCache.compute(data.fileId) { _, existingEntry ->
            if (existingEntry != null) error("File already requested before it was uploaded? This should never happen!")
            Entry(data, Instant.now(), FileState.IN_CACHE)
        }
    }

    private suspend fun Entry.doDownload() {
        val newState = try {
            LOG.info("Downloading file ${fileData.link} from S3 into cache")
            diskBackend.storeFromFlow(fileData, s3ClientService.getFile(fileData.link))
            fileCacheRepository.addFileToCache(fileData.fileId)
            FileState.IN_CACHE
        } catch (e: Exception) {
            LOG.error("An error occurred downloading ${fileData.link} from S3 into cache", e)
            FileState.ERROR
        }
        getOrCreateEntryAndRunWithState(fileData) {
            if (state != FileState.DOWNLOADING) {
                LOG.error("After downloading file ${fileData.link} encountered state $state. This should not be possible.")
                return@getOrCreateEntryAndRunWithState
            }
            changeState(newState)
            lastUsed = Instant.now()
        }
    }

    override fun getFileFlow(data: FileData, start: Long, end: Long): Flow<Buffer> = flow {
        loop@ while (true) {
            val access = getOrCreateEntryAndRunWithState(data) {
                when (state) {
                    FileState.INITIALIZED -> {
                        // We just created this entry and we need to download it
                        changeState(FileState.DOWNLOADING)
                        fileCoroutineScope.launch {
                            this@getOrCreateEntryAndRunWithState.doDownload()
                        }
                    }

                    FileState.IN_CACHE -> {
                        // Incrementing this ensures that we are protected and can read.
                        // Note: It is important that the `readCount` is always decremented after this.
                        // Our code cannot be canceled between this statement and the `try finally` block below,
                        // so it should always be the case.
                        // But we cannot handle downloading here because we need to release the lock while downloading
                        // to allow simultaneous access.
                        LOG.debug("File ${data.link} is already in cache, marking as used")
                        fileCacheRepository.markFileAsUsed(data.fileId)
                        lastUsed = Instant.now()
                        readCount++
                    }

                    FileState.ERROR -> throw InternalServerErrorException("File could not be downloaded!")
                    FileState.EVICTED -> {
                        // The entry was already evicted, we can retry immediately with a new entry
                        this.removeFromCache()
                        continue@loop
                    }

                    else -> {
                        // We wait for someone else to change the state
                    }
                }
            }

            if (access.stateWhenAccessed != FileState.IN_CACHE) {
                access.entry.waitForStateChange(access.stateWhenAccessed)
                continue
            }

            // The file was in cache and can be accessed. Start the download
            try {
                LOG.debug("Emitting data for ${data.link} from disk cache")
                emitAll(diskBackend.getFileFlow(data, start, end))
                return@flow
            } finally {
                // We launch this in a different scope so it absolutely 100% cannot be canceled, to avoid deadlocks.
                fileCoroutineScope.launch {
                    access.entry.runWithLock {
                        readCount--
                        if (readCount == 0 && state == FileState.EVICTING) {
                            changeState(FileState.EVICTING_DOWNLOADS_COMPLETED)
                        }
                    }
                }
            }
        }
    }

    suspend fun evictOld() {
        val sortedValues = currentCache.values.sorted()
        val totalSize = sortedValues.sumOf { it.fileData.size }
        val totalCount = sortedValues.size

        if (totalCount <= fileCountThreshold && totalSize <= fileSizeThreshold) {
            return
        }

        var currentCount = 0
        var currentSize = 0L

        // We always evict error entries
        val (errors, nonErrors) = sortedValues.partition { it.state == FileState.ERROR }

        // The list is sorted by the last used time.
        // The elements at the start of the `entries` list are the _least_ recently used while the
        // ones near the end are the _most_ recently used.
        // We drop from the right side of the list until the cache is full, and we are left with the
        // oldest entries that will not fit which we will evict.
        val entriesToEvict = nonErrors.dropLastWhile {
            currentCount += 1
            currentSize += it.fileData.size

            currentCount <= fileCountTarget && currentSize <= fileSizeTarget
        } + errors

        coroutineScope {
            entriesToEvict.forEach {
                launch {
                    LOG.info("Starting eviction for file ${it.fileData.link}")
                    evictFromCache(it.fileData)
                }
            }
        }
    }

    @Scheduled(every = "5m", delayed = "5m", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    fun scheduledEviction(): Uni<Void?> = suspendingVoid {
        LOG.info("Evicting old entries")
        evictOld()
    }

    fun onStart(@Observes startupEvent: StartupEvent) {
        runBlocking {
            LOG.info("Loading file cache from disk and database")
            val existingCacheData = fileCacheRepository.getFileCache().associateBy { it.file.link }
            val existingFiles = diskBackend.listFiles().associateBy { it.name }
            val fileIdsToRemoveFromCache = mutableListOf<Int>()
            val filesToKeepInCache = mutableListOf<Entry>()

            for ((link, entry) in existingCacheData) {
                val fileOnDisk = existingFiles[link]
                if (fileOnDisk == null) {
                    LOG.warn("File $link not found in disk, deleting from cache")
                    fileIdsToRemoveFromCache.add(entry.file.fileId)
                    continue
                }
                if (entry.file.size != fileOnDisk.length()) {
                    LOG.warn("File $link corrupted on disk, deleting from cache and disk")
                    fileIdsToRemoveFromCache.add(entry.file.fileId)
                    continue
                }
                filesToKeepInCache.add(
                    Entry(
                        fileData = entry.file,
                        lastUsed = entry.lastUsed,
                        initialState = FileState.IN_CACHE,
                    )
                )
            }

            for ((filename, existingFile) in existingFiles) {
                if (existingCacheData.containsKey(filename)) continue
                LOG.warn("Orphaned file $filename found on disk, deleting from disk")
                existingFile.delete()
            }
            currentCache.clear()
            currentCache.putAll(filesToKeepInCache.associateBy { it.fileData.fileId })

            fileCacheRepository.removeFilesFromCache(fileIdsToRemoveFromCache)
            evictOld()
        }
    }

    fun onStop(@Observes shutdownEvent: ShutdownEvent) {
        fileCoroutineScope.cancel()
    }
}