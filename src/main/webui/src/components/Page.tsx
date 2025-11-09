import {
    Alert,
    AlertColor,
    AppBar,
    Box,
    Container,
    CssBaseline, Dialog,
    IconButton,
    Menu,
    MenuItem, Paper, PaperProps, Snackbar,
    Toolbar,
    Typography,
    useTheme
} from "@mui/material";
import {SearchBar, SearchIconWrapper, StyledInputBase} from "./SearchBar";
import SearchIcon from '@mui/icons-material/Search';
import {AccountResponse, useUser} from "../hooks/useUser";
import {PropsWithChildren, useState} from "react";
import {MoreVert} from "@mui/icons-material";
import {useNavigate} from "react-router-dom";
import {ApiKeyDialog} from "./ApiKeyDialog";
import {CreateUserDialog} from "./CreateUserDialog";

interface SidebarMenuProps {
    onApiKeySelected: () => void
    onCreateAccountSelected: () => void
    onLogout: () => void
    user: AccountResponse | null
}

const SidebarMenu = (props: SidebarMenuProps) => {
    const [anchorEl, setAnchorEl] = useState<HTMLButtonElement | null>()
    const navigate = useNavigate()

    return <>
        <IconButton edge="end"
                    size="large"
                    color="inherit"
                    onClick={(e) => {
                        setAnchorEl(e.currentTarget)
                    }}>
            <MoreVert/>
        </IconButton>
        <Menu anchorEl={anchorEl} onClose={() => setAnchorEl(null)} open={anchorEl != null}>
            {props.user && <>
                <MenuItem onClick={() => {
                    setAnchorEl(null)
                    navigate("/account")}
                }>
                    Files
                </MenuItem>
                <MenuItem onClick={() => {
                    setAnchorEl(null)
                    props.onApiKeySelected()
                }}>
                    Get API Key
                </MenuItem>
                <MenuItem onClick={() => {
                    setAnchorEl(null)
                    navigate("/share")
                }}>
                    File Transfer
                </MenuItem>
            </>
            }
            {props.user?.admin && <>
                <MenuItem onClick={() => {
                    setAnchorEl(null)
                    props.onCreateAccountSelected()
                }}>
                    Create Account
                </MenuItem>
                <MenuItem onClick={() => {
                    setAnchorEl(null)
                    navigate("/statistics")
                }}>
                    Statistics
                </MenuItem>
            </>}
            <MenuItem onClick={props.onLogout}>
                Logout
            </MenuItem>
        </Menu>
    </>
}

type CurrentDialog = "api_key" | "create_account"

export interface AlertData {
    message: string
    color: AlertColor
}

interface PageProps extends PropsWithChildren {
    title: string
    searchBarEnabled: boolean
    onSearchChanged?: (value: string) => void
    currentAlert?: AlertData
    onAlertHidden?: () => void
    paperProps?: PaperProps
}

export const Page = (props: PageProps) => {
    const user = useUser({requireUser: false})
    const theme = useTheme()
    const [currentDialog, setCurrentDialog] = useState<CurrentDialog | null>(null)
    const [currentAlert, setCurrentAlert] = useState<AlertData | null>(null)
    const [alertOpen, setAlertOpen] = useState<boolean>(false)

    const doLogout = () => {
        window.location.href = "/api/user/logout"
    }

    let searchBar
    if (props.searchBarEnabled) {
        searchBar = <SearchBar>
            <SearchIconWrapper>
                <SearchIcon></SearchIcon>
            </SearchIconWrapper>
            <StyledInputBase placeholder="Search..." onChange={(e) => {
                if (!props.onSearchChanged) return
                props.onSearchChanged(e.currentTarget.value)
            }}/>
        </SearchBar>
    }

    let sideBar
    if (user) {
        sideBar = <SidebarMenu onLogout={doLogout}
                               onApiKeySelected={() => setCurrentDialog("api_key")}
                               onCreateAccountSelected={() => setCurrentDialog("create_account")}
                               user={user.user}/>
    }

    return <Box sx={{display: "flex", flexDirection: "column"}}>
        <CssBaseline/>

        <AppBar component="nav" sx={{p: 1}}>
            <Container maxWidth="lg" sx={{display: "flex", flexDirection: "row", alignItems: "center"}}>
                <Typography variant="h5" sx={{marginLeft: "10px"}}>
                    {props.title}
                </Typography>
                <Box sx={{flexGrow: 1, [theme.breakpoints.only("sm")]: {display: "flex"}}}>

                </Box>
                {searchBar}
                {sideBar}
            </Container>
        </AppBar>
        <Toolbar/>
        <Container maxWidth="lg" sx={{paddingTop: "32px"}}>
            <Paper sx={{display: "flex", flexDirection: "column", alignItems: "center", padding: "24px"}} {...props.paperProps}>
                {props.children}
            </Paper>
        </Container>
        <Dialog open={currentDialog !== null}
                maxWidth="lg"
                onClose={() => setCurrentDialog(null)}
        >
            {currentDialog === "api_key" && <ApiKeyDialog showAlert={(alert) => {
                setCurrentAlert(alert)
                setAlertOpen(true)
            }}/>}
            {currentDialog === "create_account" && <CreateUserDialog showAlert={(alert) => {
                setCurrentAlert(alert)
                setAlertOpen(true)
            }}/>}
        </Dialog>
        <Snackbar open={alertOpen} autoHideDuration={6000} onClose={() => {
            if (props.onAlertHidden) {
                props.onAlertHidden()
            }
            setAlertOpen(false)
        }}>
            {currentAlert ?
                <Alert onClose={() => {
                    if (props.onAlertHidden) {
                        props.onAlertHidden()
                    }
                    setAlertOpen(false)
                }} sx={{width: "100%"}} severity={currentAlert.color}>
                    {currentAlert.message}
                </Alert> : <Alert/>
            }
        </Snackbar>
    </Box>
}