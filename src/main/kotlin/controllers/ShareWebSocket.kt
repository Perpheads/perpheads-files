package com.perpheads.files.controllers

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.perpheads.files.services.ShareService
import com.perpheads.files.suspending
import io.quarkus.websockets.next.OnBinaryMessage
import io.quarkus.websockets.next.OnClose
import io.quarkus.websockets.next.OnOpen
import io.quarkus.websockets.next.OnTextMessage
import io.quarkus.websockets.next.WebSocket
import io.quarkus.websockets.next.WebSocketConnection
import io.smallrye.mutiny.Uni
import io.smallrye.mutiny.coroutines.awaitSuspending
import jakarta.annotation.security.RolesAllowed
import jakarta.enterprise.context.RequestScoped
import org.jboss.logging.Logger

@WebSocket(path = "/api/share/ws")
@RolesAllowed("user")
@RequestScoped
class ShareWebSocket(
    private val shareService: ShareService,
    private val webSocketConnection: WebSocketConnection,
) {
    companion object {
        private val LOG = Logger.getLogger(ShareWebSocket::class.java.name)
    }

    sealed class ShareWebSocketMessage(val type: String)
    class PullMessage(val count: Int) : ShareWebSocketMessage("pull")
    class ErrorMessage(val message: String) : ShareWebSocketMessage("error")
    class AnnounceResponse(val token: String) : ShareWebSocketMessage("link")
    object CompletedResponse : ShareWebSocketMessage("completed")

    @OnOpen
    fun onOpen() {}

    @OnTextMessage
    fun onTextMessage(text: String): Uni<ShareWebSocketMessage?> = suspending {
        val announceMessage = jacksonObjectMapper().readTree(text)
        val type = announceMessage["type"]?.asText() ?: return@suspending ErrorMessage("Invalid Message")
        if (type == "announce") {
            val filename = announceMessage["filename"].asText()
            val size = announceMessage["size"].asLong()
            val shareData = shareService.registerFile(webSocketConnection.id(), filename, size)
            if (shareData != null) {
                LOG.info("Announced file share file ${shareData.filename} size: ${shareData.size}")
                AnnounceResponse(shareData.token)
            } else {
                ErrorMessage("Could not register, was already registered?")
            }
        } else if (type == "completed") {
            LOG.info("File share upload completed for ${webSocketConnection.id()}")
            shareService.closeSession(webSocketConnection.id(), true)
            webSocketConnection.close().awaitSuspending()
            CompletedResponse
        } else {
            ErrorMessage("Unknown message type")
        }
    }

    @OnBinaryMessage
    fun onBinaryMessage(chunk: ByteArray): ShareWebSocketMessage? {
        if (!shareService.sendData(webSocketConnection.id(), chunk)) {
            LOG.info("Failed to send data to websocket, user probably went away.")
            webSocketConnection.close()
            return ErrorMessage("Something went wrong, user went away?")
        }
        LOG.debug("Received chunk")
        return null
    }

    @OnClose
    fun onClose() {
        LOG.info("Closing websocket connection")
        // Uncommanded close before close from completion means an error occurred
        shareService.closeSession(webSocketConnection.id(), false)
    }
}