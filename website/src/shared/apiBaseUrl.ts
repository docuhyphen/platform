const configuredApiBaseUrl = import.meta.env.VITE_API_BASE_URL as string | undefined;

export const getApiBaseUrl = (): string =>
    (configuredApiBaseUrl ?? "").replace(/\/$/, "");

