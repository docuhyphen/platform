import {useEffect, useState} from 'react';
import {
    clampZoom,
    DEFAULT_ZOOM_LEVEL,
    ENLARGED_ZOOM_STORAGE_KEY,
    INLINE_ZOOM_STORAGE_KEY,
    persistZoomPreference,
    readZoomPreference,
    ZOOM_STEP,
} from './previewZoomPreference.ts';

export const usePreviewZoom = (isEnlarged: boolean) =>
{
    const [inlineZoomLevel, setInlineZoomLevel] = useState<number>(() => readZoomPreference(INLINE_ZOOM_STORAGE_KEY));
    const [enlargedZoomLevel, setEnlargedZoomLevel] = useState<number>(() => readZoomPreference(ENLARGED_ZOOM_STORAGE_KEY));

    useEffect(() =>
    {
        persistZoomPreference(INLINE_ZOOM_STORAGE_KEY, inlineZoomLevel);
    }, [inlineZoomLevel]);

    useEffect(() =>
    {
        persistZoomPreference(ENLARGED_ZOOM_STORAGE_KEY, enlargedZoomLevel);
    }, [enlargedZoomLevel]);

    const scale = isEnlarged ? enlargedZoomLevel : inlineZoomLevel;

    const setActiveZoomLevel = (nextZoomLevel: number) =>
    {
        const clamped = clampZoom(nextZoomLevel);
        if (isEnlarged)
        {
            setEnlargedZoomLevel(clamped);
            return;
        }
        setInlineZoomLevel(clamped);
    };

    return {
        scale,
        zoomIn: () => setActiveZoomLevel(scale + ZOOM_STEP),
        zoomOut: () => setActiveZoomLevel(scale - ZOOM_STEP),
        resetZoom: () => setActiveZoomLevel(DEFAULT_ZOOM_LEVEL),
    };
};

export type PreviewZoom = ReturnType<typeof usePreviewZoom>;
