export const INLINE_ZOOM_STORAGE_KEY = 'exchanges.preview.zoom.inline';
export const ENLARGED_ZOOM_STORAGE_KEY = 'exchanges.preview.zoom.enlarged';
export const DEFAULT_ZOOM_LEVEL = 1.0;
export const ZOOM_STEP = 0.1;
const MIN_ZOOM_LEVEL = 0.5;
const MAX_ZOOM_LEVEL = 4.0;

export const clampZoom = (value: number): number =>
{
    return Math.min(Math.max(value, MIN_ZOOM_LEVEL), MAX_ZOOM_LEVEL);
};

export const readZoomPreference = (storageKey: string, fallback: number = DEFAULT_ZOOM_LEVEL): number =>
{
    if (typeof window === 'undefined')
    {
        return fallback;
    }

    const saved = window.localStorage.getItem(storageKey);
    if (!saved)
    {
        return fallback;
    }

    const parsed = Number(saved);
    if (!Number.isFinite(parsed))
    {
        return fallback;
    }

    return clampZoom(parsed);
};

export const persistZoomPreference = (storageKey: string, value: number): void =>
{
    if (typeof window === 'undefined') return;
    window.localStorage.setItem(storageKey, String(value));
};
