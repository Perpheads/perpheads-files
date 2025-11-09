import {useUser} from "../hooks/useUser";
import {Page} from "../components/Page";
import {useSearchParams} from "react-router-dom";
import {
    Fab, Pagination,
    Table,
    TableBody, TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Typography,
    useMediaQuery,
    useTheme
} from "@mui/material";
import {Add} from "@mui/icons-material";
import {useEffect, useMemo, useRef, useState} from "react";
import useApiCall, {ApiCallResponseData, makeApiCall} from "../hooks/CancellableApiCall";
import {FileResponse, GetFilesResponse} from "../data/FileResponse";
import {File} from "../components/File"
import {QueuedUpload, UploadQueue} from "../components/UploadQueue";

let uploadIdCounter: number = 0

export const AccountPage = () => {
    const user = useUser({requireUser: true})
    const theme = useTheme()
    const [searchParams, setSearchParams] = useSearchParams()
    const tinyScreen = useMediaQuery(theme.breakpoints.only('xs'))
    const smallScreen = useMediaQuery(theme.breakpoints.down('md'))
    const shouldShowDetails = useMediaQuery(theme.breakpoints.up('sm'))
    const currentPage = parseInt(searchParams.get("page") ?? "1")
    const search = searchParams.get("search") ?? ""
    const [renameCall, setRenameCall] = useState<ApiCallResponseData | null>(null)
    const [deleteCall, setDeleteCall] = useState<ApiCallResponseData | null>(null)
    const [queuedUploads, setQueuedUploads] = useState<QueuedUpload[]>([])
    const [draggedOver, setDraggedOver] = useState<boolean>(false)
    const dragCounter = useRef(0)

    const changeUrl = (newPage: number, newSearch: string) => {
        const params = new URLSearchParams()
        if (newPage !== 1) {
            params.append("page", newPage.toString())
        }
        if (newSearch.trim() !== "") {
            params.append("search", newSearch)
        }
        setSearchParams(params)
    }
    const body = useMemo(() => ({
        query: search,
        page: currentPage,
        entriesPerPage: 10,
    }), [search, currentPage])

    const files = useApiCall<GetFilesResponse>({
        url: "/api/file",
        method: "POST",
        body: body,
    })

    function doRename(file: FileResponse, newName: string) {
        setRenameCall(makeApiCall({
            method: "PUT",
            url: `/api/file/${file.fileId}/filename?filename=${encodeURIComponent(newName)}`,
            onError: () => {
                setRenameCall(null)
            },
            onLoadedCallback: () => {
                setRenameCall(null)
                files.refresh()
            }
        }))
    }

    useEffect(() => {
        return () => {
            renameCall?.cancel()
        }
    }, [renameCall])

    function doDelete(file: FileResponse) {
        setDeleteCall(makeApiCall({
            method: "DELETE",
            url: `/api/file/${file.fileId}`,
            onError: () => {
                setDeleteCall(null)
            },
            onLoadedCallback: () => {
                setDeleteCall(null)
                files.refresh()
            }
        }))
    }

    useEffect(() => {
        return () => {
            deleteCall?.cancel()
        }
    }, [deleteCall])

    const doUploadFiles = (filesToUpload: FileList) => {
        if (filesToUpload.length === 0) return
        const newQueue = queuedUploads.slice()
        for (let i = 0; i < filesToUpload.length; i++) {
            const file = filesToUpload[i]
            const uploadId = uploadIdCounter++
            const entry: QueuedUpload = {
                file: file,
                uploadId: uploadId,
            }
            newQueue.push(entry)
        }
        setQueuedUploads(newQueue)
    }

    let title
    if (smallScreen) {
        title = ""
    } else if (user.user) {
        title = `Hey there, ${user.user.name}.`
    } else {
        title = "Hey there."
    }

    return <Page title={title} searchBarEnabled={true} onSearchChanged={(newSearch) => {
        changeUrl(1, newSearch)
    }} paperProps={{
        elevation: draggedOver ? 4 : undefined,
        onDragOver: e => {
            e.preventDefault()
            e.stopPropagation()
        },
        onDragEnter: e => {
            e.preventDefault()
            e.stopPropagation()
            dragCounter.current++
            if (dragCounter.current === 1) setDraggedOver(true)
        },
        onDragLeave: e => {
            e.preventDefault()
            e.stopPropagation()
            dragCounter.current--
            if (dragCounter.current === 0) setDraggedOver(false)
        },
        onDrop: e => {
            e.preventDefault()
            e.stopPropagation()
            dragCounter.current = 0
            setDraggedOver(false)
            doUploadFiles(e.dataTransfer.files)
        }
    }}>
        <TableContainer sx={{width: "100%"}}>
            <Table>
                <TableHead>
                    <TableRow>
                        <TableCell>
                            <Typography variant="h6">Name</Typography>
                        </TableCell>
                        {shouldShowDetails && <TableCell>
                            <Typography variant="h6">Date</Typography>
                        </TableCell>}
                        {shouldShowDetails && <TableCell>
                            <Typography variant="h6">Size</Typography>
                        </TableCell>}
                        <TableCell align="right" sx={{width: "1px", whiteSpace: "nowrap"}}/>
                    </TableRow>
                </TableHead>
                <TableBody>
                    {files.data?.files.map((file) => (
                        <File key={file.link} deleteFile={doDelete}
                              renameFile={doRename}
                              showDetails={shouldShowDetails}
                              file={file}
                        />))}
                </TableBody>
            </Table>
        </TableContainer>
        <Pagination size={(smallScreen) ? "small" : "medium"}
                    sx={{marginTop: "16px"}}
                    count={files.data?.totalPages ?? 1}
                    defaultPage={files.data?.totalPages ?? 1}
                    showFirstButton={!tinyScreen}
                    showLastButton={!tinyScreen}
                    page={currentPage}
                    onChange={(_, num) => changeUrl(num, search)}/>
        {queuedUploads.length > 0 && <UploadQueue entries={queuedUploads} onUploadQueueFinished={() => {
            setQueuedUploads([])
            files.refresh()
        }}/>}
        <Fab color="secondary"
             sx={{position: "fixed", right: "32px", bottom: "32px"}}
             onClick={() => {
                 document.getElementById("file-input")?.click()
             }}>
            <Add/>
        </Fab>
        <input id="file-input" type="file" style={{display: "none"}} onChange={(e) => {
            const files = e.target.files
            if (files === null || files.length === 0) return
            doUploadFiles(files)
        }}/>
    </Page>
}