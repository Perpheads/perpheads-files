package com.perpheads.files.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.perpheads.files.controllers.ShareWebSocket
import com.perpheads.files.utils.alphaNumeric
import io.quarkus.websockets.next.OpenConnections
import io.smallrye.mutiny.coroutines.awaitSuspending
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.NotFoundException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.jboss.logging.Logger
import java.security.SecureRandom
import kotlin.jvm.optionals.getOrNull


@ApplicationScoped
class ShareService {
    companion object {
        const val CHUNK_SIZE = 65000
        const val MAX_CHUNKS_IN_FLIGHT = 100
        private val LOG = Logger.getLogger(ShareService::class.java.name)
    }

    @Inject
    lateinit var connections: OpenConnections

    class ShareFileData(
        val filename: String,
        val size: Long,
        val token: String,
    )

    private val openSessions: HashMap<String, ShareFileData> = HashMap()
    private val waitingConnections: HashMap<String, String> = HashMap()

    private class DownloadingSessionData(
        val queued: Channel<ByteArray>,
    )

    private val downloadingConnections: HashMap<String, DownloadingSessionData> = HashMap()

    private val secureRandom = SecureRandom()

    @Synchronized
    fun getShareInformation(token: String): ShareFileData? {
        val waitingConnection = waitingConnections[token] ?: return null
        return openSessions[waitingConnection]
    }

    @Synchronized
    fun registerFile(connectionId: String, filename: String, size: Long): ShareFileData? {
        if (openSessions.containsKey(connectionId)) return null
        val token = secureRandom.alphaNumeric(16)
        val shareFileData = ShareFileData(filename, size, token)
        openSessions[connectionId] = shareFileData
        waitingConnections[token] = connectionId
        return shareFileData
    }

    private suspend fun pull(connectionId: String, count: Int) {
        val connection = connections.findByConnectionId(connectionId).getOrNull()
            ?: throw NotFoundException("Connection no longer found")
        val objectMapper = jacksonObjectMapper()
        val serializedText = objectMapper.writeValueAsString(ShareWebSocket.PullMessage(count))
        connection.sendText(serializedText).awaitSuspending()
    }

    fun startDownload(token: String): Flow<ByteArray> = flow {
        val connectionId: String
        val downloadingSessionData: DownloadingSessionData
        synchronized(this@ShareService) {
            // It is okay for this to run in a synchronized context because there are no suspension points inside
            connectionId = waitingConnections.remove(token)
                ?: throw NotFoundException("Token $token could not be found")
            downloadingSessionData = DownloadingSessionData(
                queued = Channel(MAX_CHUNKS_IN_FLIGHT)
            )
            downloadingConnections[token] = downloadingSessionData
        }
        LOG.info("Found share session for $token, sending pull messages")
        pull(connectionId, MAX_CHUNKS_IN_FLIGHT)

        try {
            downloadingSessionData.queued.consumeEach { chunk ->
                LOG.debug("Sending chunk to client")
                emit(chunk)
                pull(connectionId, count = 1)
            }
        } catch (_: Exception) {
            connections.findByConnectionId(connectionId).getOrNull()?.close()?.awaitSuspending()
            error("Share session ended early")
        }
    }

    @Synchronized
    fun sendData(connectionId: String, receivedBytes: ByteArray): Boolean {
        if (receivedBytes.size > CHUNK_SIZE) return false
        val sessionData = openSessions[connectionId] ?: return false
        val downloadingConnection = downloadingConnections[sessionData.token] ?: return false
        return downloadingConnection.queued.trySend(receivedBytes).isSuccess
    }

    @Synchronized
    fun closeSession(connectionId: String, wasSuccess: Boolean) {
        val closedSession = openSessions.remove(connectionId)
        if (closedSession != null) {
            LOG.info("Closing file transfer session for ${closedSession.token}")
            waitingConnections.remove(closedSession.token)
            downloadingConnections.remove(closedSession.token)?.queued?.let { downloadChannel ->
                if (wasSuccess) {
                    downloadChannel.close()
                } else {
                    downloadChannel.cancel()
                }
            }
        }
    }
}