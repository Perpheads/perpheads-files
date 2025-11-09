import {FileDownload, FileUpload } from "@mui/icons-material";
import {Box, Card, CardContent, Typography} from "@mui/material";
import {humanReadableByteSize} from "../util";

interface SharePreviewProps {
    filename: string
    size: number
    isUpload: boolean
}

export const SharePreview = (props: SharePreviewProps) => {
    return <Card sx={{marginTop: "16px", marginBottom: "16px", minWidth: "200px"}}>
        <CardContent>
            <Box sx={{display: "flex", flexDirection: "row", alignItems: "center", justifyContent: "center", gap: "16px"}}>
                {props.isUpload && <FileUpload fontSize="large"/>}
                {!props.isUpload && <FileDownload fontSize="large"/>}
                <Box sx={{flexDirection: "column"}}>
                    <Typography variant="h5" gutterBottom>
                        {`File: ${props.filename}`}
                    </Typography>
                    <Typography variant="body1">
                        {`Size: ${humanReadableByteSize(props.size)}`}
                    </Typography>
                </Box>
            </Box>
        </CardContent>
    </Card>
}