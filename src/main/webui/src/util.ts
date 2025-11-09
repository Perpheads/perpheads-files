
const SIZE_UNITS = ["B", "kB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB"]
export function humanReadableByteSize(size: number): string {
    if (size <= 0) return "0 B"
    const digitGroup = Math.floor(Math.log10(size) / 3)
    const unit = SIZE_UNITS[digitGroup]
    const amount = (Math.round(size * 10 / (Math.pow(1000, digitGroup))) / 10).toFixed(1)
    return `${amount} ${unit}`
}

export function getLocalLink(relativePath: string): string {
    return `${window.location.protocol}//${window.location.host}${relativePath}`
}