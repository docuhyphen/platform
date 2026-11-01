import React, {useEffect, useState} from 'react';

export const usePreviewContainerWidth = (
    pdfContainerRef: React.RefObject<HTMLDivElement>,
    pdfUrl: string | null,
    isEnlarged: boolean,
    isFullscreen: boolean,
): number =>
{
    const [containerWidth, setContainerWidth] = useState<number>(0);

    useEffect(() =>
    {
        const node = pdfContainerRef.current;
        if (!node || typeof ResizeObserver === 'undefined')
        {
            return;
        }

        const measure = () =>
        {
            const style = window.getComputedStyle(node);
            const paddingLeft = parseFloat(style.paddingLeft) || 0;
            const paddingRight = parseFloat(style.paddingRight) || 0;
            const innerWidth = node.clientWidth - paddingLeft - paddingRight;
            setContainerWidth(Math.max(0, Math.floor(innerWidth)));
        };

        measure();
        const observer = new ResizeObserver(measure);
        observer.observe(node);
        return () => observer.disconnect();
    }, [pdfContainerRef, pdfUrl, isEnlarged, isFullscreen]);

    return containerWidth;
};
