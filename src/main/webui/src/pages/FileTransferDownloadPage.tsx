import {useParams} from "react-router-dom";
import {useState} from "react";
import useApiCall from "../hooks/CancellableApiCall";
import {Page} from "../components/Page";
import {Button, Typography} from "@mui/material";
import {SharePreview} from "../components/SharePreview";
import {getLocalLink} from "../util";
import { CloudDownload } from "@mui/icons-material";

interface ShareFileData {
    filename: string
    size: number
}

export const FileTransferDownloadPage = () => {
    const param = useParams()
    const token = param["token"]
    const [downloading, setDownloading] = useState(false)

    const shareInfo = useApiCall<ShareFileData>({
        method: "GET",
        url: `/api/share/${token}`,
    })

    return <Page title="Download File" searchBarEnabled={false}>
        <Typography variant="h5" gutterBottom>Downloading file:</Typography>
        {shareInfo.data && token &&
            <>
                <SharePreview filename={shareInfo.data.filename} size={shareInfo.data.size} isUpload={false}/>
                <Button sx={{marginTop: "16px"}} disabled={downloading}
                        variant="contained"
                        color="secondary"
                        endIcon={<CloudDownload/>}
                        href={getLocalLink(`/api/share/${token}/download`)}
                        onClick={() => setDownloading(true)}>
                    Download
                </Button>
            </>
        }
        {!shareInfo.data &&
            <Typography>Looks like this link no longer works =(</Typography>
        }
    </Page>
}