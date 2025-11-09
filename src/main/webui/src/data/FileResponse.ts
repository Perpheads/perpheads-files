
export interface GetFilesResponse {
    totalPages: number
    currentPage: number
    files: FileResponse[]
}

export interface FileResponse {
    fileId: number
    link: string
    filename: string
    mimeType: string
    userId?: number
    uploadDate: string
    size: number
    hasThumbnail: boolean
}