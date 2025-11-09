import {AlertData, Page} from "../components/Page";
import {useEffect, useState} from "react";
import {useUser} from "../hooks/useUser";
import {WebSocketSender} from "../WebSocketSender";
import {Button, LinearProgress, Paper, TextField, Typography} from "@mui/material";
import {SharePreview} from "../components/SharePreview";
import {Share} from "@mui/icons-material";


const preventPageClose = (e: BeforeUnloadEvent) => {
    e.preventDefault()
    return "There are file transfers in progress, are you sure you want to close this page?"
}

export const FileTransferPage = () => {
    useUser({requireUser: true})
    const [currentAlert, setCurrentAlert] = useState<AlertData | null>(null)
    const [dropEnabled, setDropEnabled] = useState<boolean>(true)
    const [createdLink, setCreatedLink] = useState<string | null>(null)
    const [dropZoneHovered, setDropZoneHovered] = useState<boolean>(false)
    const [error, setError] = useState<string | null>(null)
    const [file, setFile] = useState<File | null>(null)
    const [completed, setCompleted] = useState(false)
    const [downloadProgress, setDownloadProgress] = useState<number | null>(null)
    const [websocketSender, setWebsocketSender] = useState<WebSocketSender | null>(null)

    useEffect(() => {
        if (!completed && createdLink) {
            window.onbeforeunload = preventPageClose
        }

        return () => {
            if (window.onbeforeunload !== preventPageClose) return
            window.onbeforeunload = null
        }
    }, [createdLink, completed])

    const updateSenderCallbacks = () => {
        if (!websocketSender) return
        websocketSender.onLinkCreated = setCreatedLink
        websocketSender.onProgress = setDownloadProgress
        websocketSender.onCompleted = () => {
            setCompleted(true)
        }
        websocketSender.onError = setError
    }

    useEffect(updateSenderCallbacks)

    const createSender = (file: File) => {
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
        const host = window.location.host
        const sender = new WebSocketSender(`${protocol}//${host}/api/share/ws`, file)
        updateSenderCallbacks()
        setWebsocketSender(sender)
    }

    const downloadPercentage = (downloadProgress && file) ? (downloadProgress * 100 / file.size) : undefined


    return <Page title="File Transfer" searchBarEnabled={false} currentAlert={currentAlert ?? undefined}
                 onAlertHidden={() => setCurrentAlert(null)}>
        {!createdLink && <><input style={{display: "none"}} type="file" id="file-input"
                                  onChange={(e) => {
                                      const files = e.target.files
                                      if (!files || files.length === 0 || !dropEnabled) return
                                      setFile(files[0])
                                  }}/>
            <Paper
                elevation={(dropZoneHovered) ? 8 : 2}
                sx={{
                    width: "100%",
                    height: "300px",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    flexDirection: "row",
                    cursor: "pointer"
                }}
                onClick={e => {
                    e.stopPropagation()
                    e.preventDefault()
                    document.getElementById("file-input")?.click()
                }}
                onDragLeave={() => setDropZoneHovered(false)}
                onDragOver={e => {
                    e.preventDefault()
                    setDropZoneHovered(true)
                }}
                onDrop={e => {
                    setDropZoneHovered(false)
                    e.preventDefault()
                    if (dropEnabled) {
                        setFile(e.dataTransfer.files[0])
                    }
                }}
            >
                <Typography sx={{userSelect: "none"}} variant="h5">
                    Drop File Here
                </Typography>
            </Paper>
        </>}
        {file && <SharePreview size={file.size} filename={file.name} isUpload={true}/>}
        {dropEnabled && <Button
            disabled={!file}
            color="secondary"
            variant="contained"
            endIcon={<Share/>}
            onClick={() => {
                if (!file || !dropEnabled) return
                setDropEnabled(false)
                createSender(file)
            }}
            sx={{
                marginTop: "16px"
            }}>
            Create Link
        </Button>}
        {(!dropEnabled && createdLink && !downloadProgress) ? <>
            <Typography variant="body1">Send this link to whoever should download this file.</Typography>
            <TextField fullWidth slotProps={{input: {readOnly: true}}} label="Share Link"
                       onClick={(e) => {
                           e.stopPropagation()
                           e.preventDefault()
                       }}
                       onFocus={(e) => {
                           e.stopPropagation()
                           e.preventDefault()
                           window.navigator.clipboard.writeText(createdLink)
                           setCurrentAlert({message: "Share link copied successfully", color: "info"})
                       }}
                       value={createdLink}
            />
        </> : <></>}
        {(downloadPercentage && file && !error) ? (<>
            <Typography variant="h5" sx={{marginTop: "20px"}}>
                {completed ? "File upload completed" : "File upload in progress"}
            </Typography>
            {!completed &&
                <Typography variant="body1" sx={{marginTop: "3px"}}>
                    Note: This window needs to stay open for the file transfer to complete.
                </Typography>
            }
            <LinearProgress value={downloadPercentage} variant="determinate" sx={{width: "100%", marginTop: "4px"}}/>
            {!completed &&
                <Typography variant="body1" sx={{marginTop: "10px"}}>
                    {`Upload Progress: ${downloadPercentage.toFixed(1)}%`}
                </Typography>
            }
        </>) : <></>}
        {error &&
            <Typography variant="body1" sx={{marginTop: "20px"}}>
                An Error occurred while uploading: {error}
            </Typography>
        }
    </Page>
}