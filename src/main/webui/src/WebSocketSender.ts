import {getLocalLink} from "./util";

const CHUNKS_SIZE: number = 65000

export class WebSocketSender {
    private socket: WebSocket
    private finalCallbackCalled: boolean = false
    private sentSize: number = 0
    private file: File

    onProgress: (progress: number) => void = () => {
    }
    onError: (error: string) => void = () => {
    }
    onCompleted: () => void = () => {
    }
    onLinkCreated: (link: string) => void = () => {
    }


    constructor(path: string, file: File) {
        this.file = file
        this.socket = new WebSocket(path)
        this.socket.onopen = () => {
            // Send announce message
            this.socket.send(JSON.stringify({
                type: "announce",
                filename: file.name,
                size: file.size,
            }))
        }
        this.socket.onerror = () => {
            if (this.finalCallbackCalled) return
            this.finalCallbackCalled = true
            this.onError("An unknown error occurred")
        }
        this.socket.onclose = () => {
            if (this.finalCallbackCalled) return
            this.finalCallbackCalled = true
            this.onError("The connection was interrupted (The user probably stopped the download)")
        }
        this.socket.onmessage = (e: MessageEvent) => {
            const message = JSON.parse(e.data)
            if (message.type === "link") {
                if (!this.finalCallbackCalled) {
                    const link = getLocalLink(`/share/${message.token}`)
                    this.onLinkCreated(link)
                }
            } else if (message.type === "pull") {
                this.sendNext(message.count)
            } else if (message.type === "error") {
                if (!this.finalCallbackCalled) {
                    this.finalCallbackCalled = true
                    this.onError(message.error)
                }
            } else if (message.type === "completed") {
                if (!this.finalCallbackCalled) {
                    this.finalCallbackCalled = true
                    this.onCompleted()
                }
                this.close()
            }
        }
    }


    close() {
        this.socket?.close()
    }

    private sendNext(count: number) {
        if (this.finalCallbackCalled) return
        if (this.sentSize >= this.file.size) return
        if (this.sentSize === 0) {
            this.onProgress(0)
        }
        for (let i = 0; i < count; i++) {
            const end = Math.min((this.sentSize + CHUNKS_SIZE), this.file.size)
            this.socket.send(this.file.slice(this.sentSize, end))
            this.sentSize = end
            if (!this.finalCallbackCalled) {
                this.onProgress(this.sentSize)
            }

            if (this.sentSize >= this.file.size) {
                this.socket.send(
                    JSON.stringify({
                        type: "completed"
                    })
                )
                if (!this.finalCallbackCalled) {
                    this.onCompleted()
                    this.finalCallbackCalled = true
                }
                break
            }
        }
    }
}