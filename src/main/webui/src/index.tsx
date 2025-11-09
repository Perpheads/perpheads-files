import React, {StrictMode} from "react";
import {createRoot} from "react-dom/client";
import {createBrowserRouter, RouterProvider} from "react-router-dom";
import {UserProvider} from "./hooks/useUser";
import {createTheme, StyledEngineProvider, ThemeProvider} from "@mui/material";
import {LoginPage} from "./pages/LoginPage";
import {AccountPage} from "./pages/AccountPage";
import {StatisticsPage} from "./pages/StatisticsPage";
import {FileTransferPage} from "./pages/FileTransferPage";
import {FileTransferDownloadPage} from "./pages/FileTransferDownloadPage";
import { ContactPage } from "./pages/ContactPage";

const root = createRoot(
    document.getElementById('root') as HTMLElement
)

const router = createBrowserRouter([
    {
        path: "/",
        element: <LoginPage/>
    },
    {
        path: "/account",
        element: <AccountPage/>
    },
    {
        path: "/statistics",
        element: <StatisticsPage/>
    },
    {
        path: "/share",
        element: <FileTransferPage/>
    },
    {
        path: "/contact",
        element: <ContactPage/>
    },
    {
        path: "/share/:token",
        element: <FileTransferDownloadPage/>
    },
]);

const theme = createTheme({
    palette: {
        mode: 'dark',
    },
})

const App = () => {
    return <StrictMode>
        <StyledEngineProvider injectFirst>
            <ThemeProvider theme={theme}>
                <UserProvider>
                    <RouterProvider router={router}/>
                </UserProvider>
            </ThemeProvider>
        </StyledEngineProvider>
    </StrictMode>
}

root.render(<App/>);