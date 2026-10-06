import {useEffect, useState} from "react";
import {Alert, Box, Button, DialogContent, DialogTitle, Divider, TextField, Typography} from "@mui/material";
import {Autorenew} from "@mui/icons-material";
import {ApiCallResponseData, makeApiCall} from "../hooks/CancellableApiCall";
import {AlertData} from "./Page";

/** What has to be typed to confirm */
const CONFIRMATION = "regenerate"

interface RegenerateLinksDialogProps {
    showAlert: (alert: AlertData) => void
}

interface NewLinksResponse {
    fileCount: number
}

/** Admin action: new links for every file of every user. */
export const RegenerateLinksDialog = (props: RegenerateLinksDialogProps) => {
    const [confirmation, setConfirmation] = useState("")
    const [request, setRequest] = useState<ApiCallResponseData>()
    const [running, setRunning] = useState(false)
    const [fileCount, setFileCount] = useState<number | null>(null)

    useEffect(() => {
        return () => {
            request?.cancel()
        }
    }, [request])

    if (fileCount !== null) {
        return <>
            <DialogTitle>
                Regenerate All Links
            </DialogTitle>
            <Divider/>
            <DialogContent>
                <Typography>All {fileCount} files have new links.</Typography>
                <Button fullWidth variant="contained" sx={{marginTop: "16px"}} onClick={() => window.location.reload()}>
                    Reload page
                </Button>
            </DialogContent>
        </>
    }

    return <>
        <DialogTitle>
            Regenerate All Links
        </DialogTitle>
        <Divider/>
        <DialogContent>
            <Alert severity="warning">
                Every file of every user gets a new link. All links shared so far stop working immediately,
                including images embedded in forums or Discord. This cannot be undone.
            </Alert>
            <Box sx={{marginTop: "8px"}} component="form" onSubmit={(e) => {
                e.preventDefault()
                if (confirmation !== CONFIRMATION || running) return
                setRunning(true)
                setRequest(makeApiCall<NewLinksResponse>({
                    method: "POST",
                    url: "/api/file/regenerate-all-links",
                    onLoadedCallback: (data) => {
                        setRunning(false)
                        setFileCount(data.fileCount)
                    },
                    onError: () => {
                        setRunning(false)
                        props.showAlert({message: "Could not regenerate the links, none were changed", color: "error"})
                    }
                }))
            }}>
                <TextField margin="normal" fullWidth required autoComplete="off"
                           label={`Type "${CONFIRMATION}" to confirm`}
                           value={confirmation}
                           onChange={(e) => setConfirmation(e.target.value.trim().toLowerCase())}/>
                <Button type="submit" fullWidth variant="contained" color="error" endIcon={<Autorenew/>}
                        sx={{marginTop: "12px", marginBottom: "2px"}}
                        disabled={confirmation !== CONFIRMATION || running}>
                    {running ? "Regenerating..." : "Regenerate all links"}
                </Button>
            </Box>
        </DialogContent>
    </>
}
