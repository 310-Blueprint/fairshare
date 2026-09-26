// Built from local date because toISOString() reports the UTC date and
// would give yesterday for the first hours of a New Zealand day.
export function today() {
    const now = new Date();
    const month = String(now.getMonth() + 1).padStart(2, '0');
    const day = String(now.getDate()).padStart(2, '0');
    return `${now.getFullYear()}-${month}-${day}`;
}
