/**
 * Resolves the backend API base URL for the marketing site.
 *
 * In production the website and the API are served from different hosts, so an absolute base
 * URL is provided through the VITE_API_BASE_URL build-time variable. When it is not set (for
 * example local development behind the Vite dev proxy) an empty string is returned so requests
 * are made against the current origin.
 */
const configuredApiBaseUrl = import.meta.env.VITE_API_BASE_URL as string | undefined;

export const getApiBaseUrl = (): string =>
    (configuredApiBaseUrl ?? "").replace(/\/$/, "");

