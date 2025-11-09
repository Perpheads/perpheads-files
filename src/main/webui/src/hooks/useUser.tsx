import {createContext, PropsWithChildren, useContext, useEffect, useRef, useState} from "react";
import {ApiCallResponseData, makeApiCall} from "./CancellableApiCall";
import {useNavigate} from "react-router-dom";

export interface AccountResponse {
    communityId: string,
    name: String,
    admin: boolean,
}

export interface UserContextProps {
    user: AccountResponse | null
    isLoading: boolean
    isError: boolean
    refresh: () => void
}

export const UserContext = createContext<UserContextProps>({
    user: null,
    isLoading: false,
    isError: false,
    refresh: () => {
    },
})


export const UserProvider = ({children}: PropsWithChildren) => {
    const [apiCall, setApiCall] = useState<ApiCallResponseData>()
    const [isLoading, setIsLoading] = useState(false)
    const [isError, setIsError] = useState(false)
    const [user, setUser] = useState<AccountResponse>()
    const loadingData = useRef({
        loading: false
    })

    useEffect(() => {
        return () => {
            apiCall?.cancel()
        }
    }, [apiCall])

    const refresh = () => {
        if (loadingData.current.loading) {
            return
        }
        loadingData.current.loading = true
        setIsLoading(true)
        setApiCall(makeApiCall({
            url: "/api/user",
            onLoadedCallback: (account: AccountResponse) => {
                loadingData.current.loading = false
                setIsLoading(false)
                setUser(account)
            },
            onError: () => {
                loadingData.current.loading = false
                setIsError(true)
                setIsLoading(false)
            }
        }))
    }

    return (
        <UserContext.Provider value={{
            user: user ?? null,
            isLoading: isLoading,
            isError: isError,
            refresh: refresh,
        }}>
            {children}
        </UserContext.Provider>
    )
}


export interface UseUserOptions {
    requireUser?: boolean
}

export function useUser(options: UseUserOptions = {}): UserContextProps {
    const context = useContext(UserContext)
    const navigate = useNavigate()
    const requireUser = options.requireUser ?? true

    useEffect(() => {
        const currentUser = context.user
        if (!context.isLoading && !context.isError && !currentUser) {
            context.refresh()
        } else if (requireUser && context.isError) {
            navigate("/")
        }
    }, [context, options, requireUser, navigate])

    return {
        user: context.user,
        isLoading: context.isLoading,
        isError: context.isError,
        refresh: context.refresh
    }
}