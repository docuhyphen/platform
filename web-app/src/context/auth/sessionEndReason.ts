export function readSessionEndReason(error: unknown): string | undefined
{
    if (typeof error !== 'object' || error === null || !('reasonCode' in error)) return undefined;
    const reasonCode = error.reasonCode;
    return typeof reasonCode === 'string' && reasonCode.length > 0 ? reasonCode : undefined;
}

export function sessionExpiredPath(reason: string | undefined | null): string
{
    return reason
        ? `/app-session-expired?reason=${encodeURIComponent(reason)}`
        : '/app-session-expired';
}
