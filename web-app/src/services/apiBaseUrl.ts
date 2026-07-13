const configuredApiBaseUrl = import.meta.env.VITE_API_BASE_URL as string | undefined;

const isLocalNetworkHost = (hostname: string): boolean =>
{
    if (hostname === "localhost" || hostname === "127.0.0.1" || hostname === "::1") return true;
    if (/^10\./.test(hostname) || /^192\.168\./.test(hostname)) return true;

    const private172Match = hostname.match(/^172\.(\d{1,3})\./);
    if (!private172Match) return false;
    const secondOctet = Number(private172Match[1]);
    return secondOctet >= 16 && secondOctet <= 31;
};

export const resolveApiBaseUrl = (
    configuredUrlValue: string | undefined,
    isDevelopment: boolean,
    browserHostname: string | undefined,
): string =>
{
    if (!configuredUrlValue) return "";
    if (!isDevelopment || !browserHostname) return configuredUrlValue;

    try
    {
        const configuredUrl = new URL(configuredUrlValue);
        if (isLocalNetworkHost(configuredUrl.hostname) && isLocalNetworkHost(browserHostname))
        {
            configuredUrl.hostname = browserHostname;
            return configuredUrl.toString().replace(/\/$/, "");
        }
    }
    catch
    {
        return configuredUrlValue;
    }

    return configuredUrlValue;
};

export const getApiBaseUrl = (): string => resolveApiBaseUrl(
    configuredApiBaseUrl,
    import.meta.env.DEV,
    typeof window === "undefined" ? undefined : window.location.hostname,
);
