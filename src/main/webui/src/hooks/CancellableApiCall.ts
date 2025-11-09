import {Dispatch, useEffect, useState} from "react";

interface ApiCallData<T> {
    url: string
    method?: string
    body?: any
    shouldRedirectOnUnauthorized?: boolean
    onLoadedCallback: (data: T) => void
    onError: () => void
}

export interface ApiCallResponseData {
    cancel: () => void
}

export function makeApiCall<T>(apiCallData: ApiCallData<T>): ApiCallResponseData {
    const controller = new AbortController()

    let bodyStr = (apiCallData.body != null) ? JSON.stringify(apiCallData.body) : null
    let headers = new Headers()
    if (apiCallData.body != null) {
        headers.set("Content-Type", "application/json")
    }

    async function doApiCall() {
        try {
            let result = await fetch(apiCallData.url, {
                method: apiCallData.method ?? "get",
                body: bodyStr,
                headers: headers,
                signal: controller.signal
            })
            if ((result.status === 401 || result.status === 403) && apiCallData.shouldRedirectOnUnauthorized) {
                window.location.href = "/"
            } else if (result.status >= 300) {
                apiCallData.onError()
            } else if (result.headers.get("Content-Type")?.startsWith("application/json")) {
                apiCallData.onLoadedCallback(await result.json() as T)
            } else {
                apiCallData.onLoadedCallback(undefined as T)
            }
        } catch (e: any) {
            if (e.name !== 'AbortError') {
                apiCallData.onError()
            }
        }
    }

    void doApiCall()

    return {
        cancel: () => {
            controller.abort()
        }
    }
}


interface UseApiCallData<T> {
    initialUrl?: string
    url?: string
    method?: string
    initialData?: T | null
    redirectOnUnauthorized?: boolean
    initialBody?: any
    body?: any
    onLoadedCallback?: (data: T) => void
    deferLoading?: boolean // set to true to prevent loading initially
}

export interface UseApiCallResponseData<T> {
    data: T | null,
    isLoading: boolean,
    isError: boolean
    setUrl: Dispatch<string>
    setBody: Dispatch<any>
    refresh: () => void
}


function useApiCall<T>(apiCallData: UseApiCallData<T>): UseApiCallResponseData<T> {
    const [data, setData] = useState(apiCallData.initialData ?? null)
    const [urlState, setUrl] = useState(apiCallData.initialUrl)
    const [bodyState, setBody] = useState(apiCallData.initialBody ?? null)
    const [isLoading, setIsLoading] = useState((apiCallData.url ?? apiCallData.initialUrl) != null && !apiCallData.deferLoading)
    const [isError, setIsError] = useState(false)
    const [refreshCount, setRefreshCount] = useState(0)


    const url = apiCallData.url ?? urlState
    const body = apiCallData.body ?? bodyState
    const shouldRedirectOnUnauthorized = apiCallData.redirectOnUnauthorized ?? true

    useEffect(() => {
        setIsLoading(true)
        setIsError(false)
        if (!url || apiCallData.deferLoading === true) {
            setIsError(false)
            setData(null)
            return
        }
        return makeApiCall<T>({
            method: apiCallData.method,
            url: url,
            body: body,
            shouldRedirectOnUnauthorized: shouldRedirectOnUnauthorized,
            onLoadedCallback: (newData) => {
                if (newData) {
                    setData(newData)
                }
                setIsLoading(false)
            },
            onError: () => {
                setData(null)
                setIsError(true)
                setIsLoading(false)
            }
        }).cancel
    }, [url, body, refreshCount, shouldRedirectOnUnauthorized, setIsLoading, setIsError, apiCallData.method, apiCallData.deferLoading]);

    const refresh = () => {
        setRefreshCount(refreshCount + 1)
        setIsLoading(true)
    }

    return {data, isLoading, isError, setUrl, setBody, refresh}
}

export default useApiCall;