import {
    Card,
    CardContent,
    CardHeader,
    Divider,
    LinearProgress,
    List,
    ListItem,
    ListItemText,
    useTheme
} from "@mui/material";
import {useEffect, useState} from "react";


export interface QueuedUpload {
    file: File,
    uploadId: number,
}

export interface UploadQueueEntryProps {
    file: File,
    uploadId: number,
    onCompleted: (success: boolean) => void,
    shouldStart: boolean,
}

export const UploadQueueEntry = (props: UploadQueueEntryProps) => {
    const filename = props.file.name
    const [progress, setProgress] = useState<number>(0)
    const [error, setError] = useState<string | null>(null)

    useEffect(() => {
        if (!props.shouldStart) return
        const form = new FormData()
        form.append("file", props.file)

        const xhr = new XMLHttpRequest()
        xhr.open("POST", "/upload", true)

        xhr.upload.onprogress = (event) => {
            if (event.lengthComputable) {
                const percent = Math.round((event.loaded / event.total) * 100);
                setProgress(percent)
            }
        }

        xhr.onload = () => {
            if (xhr.status >= 200 && xhr.status < 300) {
                setProgress(1)
                props.onCompleted(true)
            } else {
                setError("Upload failed")
            }
        }
        xhr.onerror = () => {
            props.onCompleted(false)
            setError("network error")
        }
        xhr.send(form)

        return () => {
            xhr.abort()
        }
    }, [props.file, props.shouldStart])

    return <ListItemText primary={filename}
                         secondary={<LinearProgress
                             value={progress}
                             variant="determinate"/>}/>
}


interface UploadQueueProps {
    entries: QueuedUpload[]
    onUploadQueueFinished: () => void
}

export const UploadQueue = (props: UploadQueueProps) => {
    const theme = useTheme()
    const [queuedUploads, setQueuedUploads] = useState<QueuedUpload[]>(props.entries)
    const [startedUploadId, setStartedUploadId] = useState<number | undefined>(queuedUploads.at(0)?.uploadId)


    const onUploadFinished = (entry: QueuedUpload): void => {
        const newUploadQueue = queuedUploads.filter(u => u.uploadId !== entry.uploadId)
        setQueuedUploads(newUploadQueue)
        if (newUploadQueue.length > 0) {
            setStartedUploadId(newUploadQueue[0].uploadId)
        } else {
            props.onUploadQueueFinished()
        }
    }

    return <Card sx={{
        position: 'fixed',
        bottom: "32px",
        left: "32px",
        [theme.breakpoints.down("md")]: {
            right: "32px",
        },
        [theme.breakpoints.up("lg")]: {
            width: "100%",
            maxWidth: "600px",
        }
    }}>
        <CardHeader title="Upload Queue"/>
        <Divider variant="fullWidth"/>
        <CardContent>
            <List>
                {queuedUploads.map(entry => (
                    <ListItem key={entry.uploadId}>
                        <UploadQueueEntry file={entry.file} onCompleted={() => {
                            onUploadFinished(entry)
                        }} uploadId={entry.uploadId} shouldStart={startedUploadId === entry.uploadId}/>
                    </ListItem>
                ))}
            </List>
        </CardContent>
    </Card>
}