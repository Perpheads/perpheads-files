import useApiCall from "../hooks/CancellableApiCall";
import {Page} from "../components/Page";
import {
    Box,
    Divider,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Typography
} from "@mui/material";
import {humanReadableByteSize} from "../util";

interface FileOverallStatistics {
    fileCount: number
    storageUsed: number
}

interface FileUserStatistics {
    name: string
    communityId: string
    fileCount: number
    storageUsed: number
}

interface FileStatisticsResponse {
    userStatistics: FileUserStatistics[]
    overallStatistics: FileOverallStatistics
}


interface StatisticsPageProps {}

export const StatisticsPage = (props: StatisticsPageProps) => {
    const statistics = useApiCall<FileStatisticsResponse>({
        method: "GET",
        url: "/api/file/statistics",
    })

    return <Page searchBarEnabled={false} title={"File Statistics"}>
        <Box sx={{display: "flex", flexDirection: "column", width: "100%"}}>
            <Typography variant="h4" gutterBottom>Statistics</Typography>
            <Divider/>
            {statistics.data && (
                <>
                    <Typography variant="h5" gutterBottom>Overall Statistics</Typography>
                    <Typography variant="body1">{`Total file count: ${statistics.data.overallStatistics.fileCount}`}</Typography>
                    <Typography variant="body1">{`Total storage used: ${humanReadableByteSize(statistics.data.overallStatistics.storageUsed)}`}</Typography>
                    <Divider sx={{marginTop: "16px", marginBottom: "16px"}}/>
                    <Typography variant="h5" gutterBottom>User Statistics (Top 100)</Typography>
                    <TableContainer>
                        <Table>
                            <TableHead>
                                <TableRow>
                                    <TableCell>Name</TableCell>
                                    <TableCell>File Count</TableCell>
                                    <TableCell>Storage Used</TableCell>
                                </TableRow>
                            </TableHead>
                            <TableBody>
                                {statistics.data.userStatistics.map((entry) => (
                                    <TableRow key={entry.communityId}>
                                        <TableCell>{entry.name}</TableCell>
                                        <TableCell>{entry.fileCount}</TableCell>
                                        <TableCell>{humanReadableByteSize(entry.storageUsed)}</TableCell>
                                    </TableRow>
                                ))}
                            </TableBody>
                        </Table>
                    </TableContainer>
                </>
            )}
        </Box>
    </Page>
}