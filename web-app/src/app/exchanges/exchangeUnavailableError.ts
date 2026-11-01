export const isExchangeUnavailableError = (error: unknown): boolean =>
{
    const status = (error as { response?: { status?: number }, status?: number })?.response?.status
        ?? (error as { status?: number })?.status;
    return status === 403 || status === 404;
};
