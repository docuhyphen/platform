import React, {useCallback, useEffect, useMemo, useState} from 'react';
import {
    DEFAULT_PAGE_HEIGHT,
    DEFAULT_PAGE_WIDTH,
    PageDimensions,
    PagePresentationProps,
} from './previewPageVirtualization.ts';

export const usePreviewPageLayout = (
    pdfContainerRef: React.RefObject<HTMLDivElement>,
    isMobile: boolean,
    containerWidth: number,
    scale: number,
) =>
{
    const [pageDimensions, setPageDimensions] = useState<PageDimensions>({
        width: DEFAULT_PAGE_WIDTH,
        height: DEFAULT_PAGE_HEIGHT,
    });

    const handlePageLoadSuccess = useCallback((pdfPage: { width: number; height: number }) =>
    {
        setPageDimensions((prev) =>
        {
            const isDefault = prev.width === DEFAULT_PAGE_WIDTH && prev.height === DEFAULT_PAGE_HEIGHT;
            if (isDefault || Math.abs(prev.width - pdfPage.width) > 1 || Math.abs(prev.height - pdfPage.height) > 1)
            {
                return {width: pdfPage.width, height: pdfPage.height};
            }
            return prev;
        });
    }, []);

    const fitToWidth = isMobile && containerWidth > 0;
    const fitWidthPx = fitToWidth ? containerWidth : undefined;

    const placeholderDimensions = useMemo(() =>
    {
        if (fitToWidth && pageDimensions.width > 0)
        {
            const ratio = pageDimensions.height / pageDimensions.width;
            return {
                width: `${fitWidthPx}px`,
                height: `${Math.round((fitWidthPx ?? 0) * ratio)}px`,
            };
        }
        return {
            width: `${pageDimensions.width * scale}px`,
            height: `${pageDimensions.height * scale}px`,
        };
    }, [pageDimensions, scale, fitToWidth, fitWidthPx]);

    useEffect(() =>
    {
        pdfContainerRef.current?.style.setProperty("--pdf-page-placeholder-width", placeholderDimensions.width);
        pdfContainerRef.current?.style.setProperty("--pdf-page-placeholder-height", placeholderDimensions.height);
    }, [pdfContainerRef, placeholderDimensions]);

    const pagePresentationProps: PagePresentationProps = fitToWidth
        ? {width: fitWidthPx}
        : {scale};

    return {handlePageLoadSuccess, pagePresentationProps};
};

export type PreviewPageLayout = ReturnType<typeof usePreviewPageLayout>;
