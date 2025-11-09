import {Page} from "../components/Page";
import {Box, Link, Typography} from "@mui/material";

export const ContactPage = () => {
    // TODO: Maybe fetch from server
    const email = "fredy@perpheads.com"

    return <Page title="Contact" searchBarEnabled={false}>
        <Box>
            <Typography variant="h4" gutterBottom>
                Copyright Infringement
            </Typography>
            <Typography variant="body1">
                If you think any of the files hosted on this page violate copyright or should be removed
                for any other reason, please contact me at&nbsp;
                <Link href={`mailto:${email}`}>{email}</Link>.
            </Typography>

            <Typography variant="h4" gutterBottom sx={{marginTop: "16px"}}>
                Account Creation
            </Typography>
            <Typography variant="body1">
                Please note that this website is currently on an invite only basis. As such, please do
                not contact me to get an account.
            </Typography>
        </Box>
    </Page>
}