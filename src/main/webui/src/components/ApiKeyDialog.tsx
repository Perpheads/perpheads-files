import {useEffect, useState} from "react";
import {
    Box,
    Button,
    DialogContent,
    DialogTitle,
    Divider,
    TextField,
    useTheme
} from "@mui/material";
import useApiCall, {ApiCallResponseData, makeApiCall} from "../hooks/CancellableApiCall";
import {ContentCopy, Refresh} from "@mui/icons-material";
import {AlertData} from "./Page";

interface ApiKeyDialogProps {
    showAlert: (alert: AlertData) => void
}

interface CreateApiKeyResponse {
    apiKey: string;
}

interface GetApiKeyResponse {
    apiKey: string;
}

const defaultApiKey = "None Set"
const loadingApiKey = "Loading"

export const ApiKeyDialog = (props: ApiKeyDialogProps) => {
    const apiKeyApiCall = useApiCall<GetApiKeyResponse>({
        url: "/api/user/api-key",
    })
    const apiKey = (apiKeyApiCall.isLoading) ? loadingApiKey : (apiKeyApiCall.data?.apiKey) ? apiKeyApiCall.data?.apiKey : defaultApiKey
    const theme = useTheme()

    const [generateRequest, setGenerateRequest] = useState<ApiCallResponseData>()

    useEffect(() => {
        return () => {
            generateRequest?.cancel()
        }
    }, [generateRequest])

    const generateApiKey = () => {
        setGenerateRequest(
            makeApiCall({
                method: "POST",
                url: "/api/user/api-key",
                shouldRedirectOnUnauthorized: true,
                onLoadedCallback: (_: CreateApiKeyResponse) => {
                    apiKeyApiCall.refresh()
                },
                onError: () => {
                    props.showAlert({message: "Error generating API key", color: "error"})
                }
            })
        )
    }

    const copyShareXConfig = () => {
        if (apiKey === defaultApiKey || apiKey === loadingApiKey) {
            props.showAlert({message: "Please generate an API key first", color: "warning"})
            return
        }
        const location = window.location.origin
        window.navigator.clipboard.writeText(
            `{
  "Version": "14.0.1",
  "DestinationType": "ImageUploader, TextUploader, FileUploader",
  "RequestMethod": "POST",
  "RequestURL": "${location}/upload",
  "Headers": {
    "API-KEY": "${apiKey}"
  },
  "Body": "MultipartFormData",
  "FileFormName": "file",
  "URL": "${location}/{json:link}"
}`
        )
        props.showAlert({message: "ShareX template copied to clipboard", color: "info"})
    }

    return <>
        <DialogTitle>
            API Key
        </DialogTitle>
        <Divider/>
        <DialogContent><TextField fullWidth slotProps={{input: {readOnly: true}}} label="API Key"
                       onClick={(e) => {
                           e.preventDefault()
                           e.stopPropagation()
                       }}
                       onFocus={(e) => {
                           if (apiKey === defaultApiKey || apiKey === loadingApiKey) {
                               props.showAlert({message: "Please generate an API key first", color: "warning"})
                               return
                           }
                           e.preventDefault()
                           e.stopPropagation()
                           window.navigator.clipboard.writeText(apiKey)
                           props.showAlert({message: "API Key copied successfully", color: "info"})
                       }}
                       value={apiKey}/>
            <Box sx={{
                display: "flex",
                flexDirection: "column",
                [theme.breakpoints.up("md")]: {
                    flexDirection: "row",
                },
                gap: "10px",
                justifyContent: "center",
                alignItems: "center",
                marginTop: "32px",
                marginRight: "16px",
                marginLeft: "16px",
            }}>
                <Button color="warning" variant="contained" endIcon={<Refresh/>} onClick={() => {
                    generateApiKey()
                }} sx={{
                    [theme.breakpoints.down("md")]: {
                        width: "100%"
                    },
                }}>
                    Regenerate API Key
                </Button>
                <Button color="info" variant="contained" endIcon={<ContentCopy/>} onClick={() => {
                    copyShareXConfig()
                }} sx={{
                    [theme.breakpoints.down("md")]: {
                        width: "100%"
                    },
                }}>
                    Copy ShareX Config
                </Button>
            </Box>
        </DialogContent>
    </>
}