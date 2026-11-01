const CSRF_COOKIE_NAME = 'csrf_token';
const CSRF_HEADER_NAME = 'X-CSRF-Token';

const SAFE_METHODS = new Set(['get', 'head', 'options']);

export const readCsrfToken = (): string | null =>
{
    if (typeof document === 'undefined')
    {
        return null;
    }

    const match = document.cookie
        .split(';')
        .map(entry => entry.trim())
        .find(entry => entry.startsWith(`${CSRF_COOKIE_NAME}=`));

    if (!match)
    {
        return null;
    }

    const rawValue = match.slice(CSRF_COOKIE_NAME.length + 1);
    return rawValue ? decodeURIComponent(rawValue) : null;
};

export const requiresCsrfHeader = (method: string | undefined): boolean =>
    !SAFE_METHODS.has((method ?? 'get').toLowerCase());

export const csrfHeaderName = CSRF_HEADER_NAME;

