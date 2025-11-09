import {useEffect, useMemo, useState} from "react";
import {Box, Button, DialogContent, DialogTitle, Divider, TextField} from "@mui/material";
import {ApiCallResponseData, makeApiCall} from "../hooks/CancellableApiCall";
import {AlertData} from "./Page";
import { Create } from "@mui/icons-material";

interface CreateUserDialogProps {
    showAlert: (alert: AlertData) => void
}

export const CreateUserDialog = (props: CreateUserDialogProps) => {
    const [name, setName] = useState("")
    const [communityId, setCommunityId] = useState("")
    const [buttonEnabled, setButtonEnabled] = useState(true)

    const [request, setRequest] = useState<ApiCallResponseData>()
    const validName = useMemo(() => {
        return /^[a-zA-Z0-9]{3,25}$/.test(name)
    }, [name])
    const validCommunityId = useMemo(() => {
        return /^[0-9]{17}$/.test(communityId)
    }, [communityId])

    useEffect(() => {
        return () => {
            request?.cancel()
        }
    }, [request])

    return <>
        <DialogTitle>
            Create New User
        </DialogTitle>
        <Divider/>
        <DialogContent>
            <Box sx={{marginTop: "2px"}} component="form" onSubmit={(e) => {
                e.preventDefault()
                if (!validCommunityId || !validName) return
                setButtonEnabled(false)
                setRequest(makeApiCall({
                    method: "POST",
                    url: "/api/user",
                    body: {
                        name: name,
                        communityId: communityId,
                    },
                    onLoadedCallback: () => {
                        props.showAlert({message: "User created successfully", color: "info"})
                        setCommunityId("")
                        setName("")
                        setButtonEnabled(true)
                    },
                    onError: () => {
                        props.showAlert({message: "Error creating User", color: "error"})
                        setButtonEnabled(true)
                    }
                }))
            }}>
                <TextField margin="normal" fullWidth required name="Name" type="text" label="Name"
                           error={name.length > 0 && !validName}
                           helperText={name.length > 0 && !validName && "Invalid Name"}
                           onChange={(e) => setName(e.target.value)}
                           value={name}/>
                <TextField margin="normal" fullWidth required name="CommunityID" type="text" label="Community ID"
                           error={communityId.length > 0 && !validCommunityId}
                           helperText={communityId.length > 0 && !validCommunityId && "Invalid Community ID"}
                           onChange={(e) => setCommunityId(e.target.value.trim())}
                           value={communityId}/>

                <Button type="submit" fullWidth variant="contained" sx={{marginTop: "12px", marginBottom: "2px"}} endIcon={<Create/>} disabled={!buttonEnabled}>
                    Create Account
                </Button>
            </Box>
        </DialogContent>
    </>
}