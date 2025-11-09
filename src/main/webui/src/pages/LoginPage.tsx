import {useUser} from "../hooks/useUser";
import {useEffect} from "react";
import {useNavigate} from "react-router-dom";
import {Box, Button, Container, CssBaseline, FormHelperText, Link, Paper, Typography, useTheme} from "@mui/material";
import { Login } from "@mui/icons-material";

const LoginCard = () => {
    const theme = useTheme()

    const logoPath = (theme.palette.mode === "dark") ? "/logo-wide-dark.png" : "/logo-wide-light.png"
    const params = new URLSearchParams(window.location.search)

    const login = () => {
      window.location.href = "/api/user/steam"
    }

    return <Box sx={{display: "flex", flexDirection: "column", alignItems: "center"}}>
        <Paper sx={{p: 4, display: "flex", mt: 16, flexDirection: "column", alignItems: "center"}}>
            <img style={{maxWidth: "90%", marginTop: "30px", marginBottom: "30px"}} src={logoPath} alt="logo"/>


            <Typography variant="h4" component="h1">Perpheads Files</Typography>
            <Button variant="contained"
                    color="primary"
                    sx={{ minWidth: "90%", mt: 3, mb: 1 }}
                    onClick={login}
                    endIcon={<Login/>}
            >
                Login with Steam
            </Button>
            {params.has("no_account") ? <FormHelperText error>It looks like you do not have an account.</FormHelperText> : <></>}
        </Paper>
    </Box>
}


export const LoginPage = () => {
    const navigate = useNavigate()
    const user = useUser({requireUser: false})

    useEffect(() => {
        if (user.user) {
            navigate("/account")
        }
    }, [user, navigate])

    return <>
        <Container maxWidth="sm" component="main">
            <CssBaseline/>
            <LoginCard/>
        </Container>
        <Link href="/contact"
              variant="h5"
              underline="none"
              onClick={(e) => {
                  e.preventDefault()
                  navigate("/contact")
              }}
              sx={{position: "absolute", right: "15px", bottom: "15px"}}>
            Contact
        </Link>
    </>
}