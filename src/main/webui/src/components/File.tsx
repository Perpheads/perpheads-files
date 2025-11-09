import {DeleteOutline, Edit} from "@mui/icons-material";
import {Box, IconButton, Link, TableCell, TableRow, TextField} from "@mui/material";
import {useState} from "react";
import {FileResponse} from "../data/FileResponse";
import {humanReadableByteSize} from "../util";

interface FileProps {
    file: FileResponse
    deleteFile: (file: FileResponse) => void
    renameFile: (file: FileResponse, newName: string) => void
    showDetails: boolean
}

function validateFilename(name: string): boolean {
    return /^[-_.A-Za-z0-9 ()]{1,200}$/.test(name)
}

export const File = (props: FileProps) => {
    const [editing, setEditing] = useState(false)
    const [invalidFilename, setInvalidFilename] = useState(false)
    const indexOfLastDot = props.file.filename.lastIndexOf(".")
    let filenameWithoutExtension = props.file.filename
    let extension = ""

    if (indexOfLastDot > 0) {
        extension = filenameWithoutExtension.substring(indexOfLastDot)
        filenameWithoutExtension = filenameWithoutExtension.substring(0, indexOfLastDot)
    }

    const date = new Date(props.file.uploadDate)
    const localizedDate = date.toLocaleString()

    let thumbnailLink = "/thumbnail.png"
    if (props.file.hasThumbnail) {
        thumbnailLink = `/thumbnail/${props.file.link}`
    }

    return <TableRow>
        <TableCell sx={{paddingTop: 1, paddingBottom: 1}}>
            <Box sx={{display: 'flex', flexDirection: 'row', alignItems: 'center'}}>
                <img src={thumbnailLink} style={{marginRight: "20px", maxWidth: "48px"}} alt="thumbnail"/>
                {editing &&
                    <TextField margin="none" required autoFocus hiddenLabel fullWidth name="Filename" variant="standard"
                               size="small" label="Filename" defaultValue={filenameWithoutExtension}
                               error={invalidFilename}
                               helperText={(invalidFilename) ? "Invalid filename" : undefined}
                               onKeyDown={(e) => {
                                   if (e.key !== "Enter") return
                                   const target = e.target as HTMLInputElement
                                   const newName = target.value + extension
                                   if (props.file.filename !== newName) {
                                       if (validateFilename(newName)) {
                                           setInvalidFilename(false)
                                           props.renameFile(props.file, newName)
                                           target.blur()
                                       } else {
                                           setInvalidFilename(true)
                                       }
                                   } else {
                                       target.blur()
                                   }
                               }}
                               onBlur={() => {
                                   setEditing(false)
                                   setInvalidFilename(false)
                               }}
                    />
                }
                {!editing && <Link href={`/${props.file.link}`} target="_blank" underline="none"
                                   sx={{overflowWrap: "anywhere"}}>{props.file.filename}</Link>}
            </Box>
        </TableCell>
        {props.showDetails && <TableCell>
            {localizedDate}
        </TableCell>}
        {props.showDetails && <TableCell>
            {humanReadableByteSize(props.file.size)}
        </TableCell>}
        <TableCell>
            <Box sx={{display: 'flex', flexDirection: 'row', alignItems: 'center'}}>
                <IconButton size="small" color="info" onClick={() => {
                    setEditing(!editing)
                    setInvalidFilename(false)
                }}>
                    <Edit/>
                </IconButton>
                <IconButton size="small" color="error" onClick={() => {
                    props.deleteFile(props.file)
                }}>
                    <DeleteOutline/>
                </IconButton>
            </Box>
        </TableCell>
    </TableRow>
}