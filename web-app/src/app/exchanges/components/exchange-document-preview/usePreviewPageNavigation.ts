import React, {useCallback, useEffect, useRef, useState} from 'react';
import {PROGRAMMATIC_SCROLL_SETTLE_MS} from './previewPageVirtualization.ts';
import {usePreviewPageVirtualization} from './usePreviewPageVirtualization.ts';

export const usePreviewPageNavigation = (pdfUrl: string | null) =>
{
    const pdfContainerRef = useRef<HTMLDivElement>(null);
    const [numPages, setNumPages] = useState<number>(0);
    const [currentPage, setCurrentPage] = useState<number>(1);
    const [pageInput, setPageInput] = useState<string>('1');
    const [isEditingPageInput, setIsEditingPageInput] = useState<boolean>(false);
    const scrollTrackingRafRef = useRef<number | null>(null);
    const programmaticScrollTimeoutRef = useRef<number | null>(null);
    const isProgrammaticScrollRef = useRef(false);
    const lastLoadedUrlRef = useRef<string | null>(null);
    const {
        visiblePages,
        pageElementsRef,
        registerPageRef,
        revealPagesAround,
        resetVisiblePages,
    } = usePreviewPageVirtualization(pdfContainerRef, numPages, currentPage, pdfUrl);

    useEffect(() =>
    {
        return () =>
        {
            if (scrollTrackingRafRef.current)
            {
                window.cancelAnimationFrame(scrollTrackingRafRef.current);
            }
            if (programmaticScrollTimeoutRef.current)
            {
                window.clearTimeout(programmaticScrollTimeoutRef.current);
            }
        };
    }, []);

    const markProgrammaticScroll = useCallback(() =>
    {
        isProgrammaticScrollRef.current = true;
        if (programmaticScrollTimeoutRef.current)
        {
            window.clearTimeout(programmaticScrollTimeoutRef.current);
        }
        programmaticScrollTimeoutRef.current = window.setTimeout(() =>
        {
            isProgrammaticScrollRef.current = false;
        }, PROGRAMMATIC_SCROLL_SETTLE_MS);
    }, []);

    const resetToFirstPage = useCallback(() =>
    {
        setCurrentPage(1);
        setPageInput('1');
        resetVisiblePages();
        lastLoadedUrlRef.current = null;
    }, [resetVisiblePages]);

    const scrollCurrentPageIntoView = useCallback(() =>
    {
        pageElementsRef.current.get(currentPage)?.scrollIntoView({block: 'start'});
    }, [currentPage, pageElementsRef]);

    const onDocumentLoadSuccess = ({numPages: loadedPageCount}: { numPages: number }) =>
    {
        setNumPages(loadedPageCount);

        if (lastLoadedUrlRef.current !== pdfUrl)
        {
            lastLoadedUrlRef.current = pdfUrl;
            setCurrentPage(1);
            setPageInput('1');
            resetVisiblePages();
            return;
        }

        const restorePage = Math.min(Math.max(currentPage, 1), loadedPageCount);
        revealPagesAround(restorePage, loadedPageCount);
        markProgrammaticScroll();
        window.requestAnimationFrame(() =>
        {
            window.requestAnimationFrame(() =>
            {
                pageElementsRef.current.get(restorePage)?.scrollIntoView({block: 'start'});
            });
        });
    };

    const goToPage = useCallback((pageNumber: number) =>
    {
        if (pageNumber >= 1 && pageNumber <= numPages)
        {
            revealPagesAround(pageNumber, numPages);

            const pageElement = pageElementsRef.current.get(pageNumber);
            if (pageElement)
            {
                markProgrammaticScroll();
                pageElement.scrollIntoView({behavior: 'smooth', block: 'start'});
            }

            setCurrentPage(pageNumber);
            setPageInput(String(pageNumber));
        }
    }, [numPages, revealPagesAround, pageElementsRef, markProgrammaticScroll]);

    const handlePageInputChange = (event: React.ChangeEvent<HTMLInputElement>) =>
    {
        setPageInput(event.target.value.replace(/[^0-9]/g, ''));
    };

    const commitPageInput = () =>
    {
        const parsed = parseInt(pageInput, 10);
        if (Number.isNaN(parsed))
        {
            setPageInput(String(currentPage));
            return;
        }

        goToPage(Math.min(Math.max(parsed, 1), Math.max(numPages, 1)));
    };

    const handlePdfScroll = () =>
    {
        if (isProgrammaticScrollRef.current) return;
        if (!pdfContainerRef.current || numPages <= 0) return;

        if (scrollTrackingRafRef.current)
        {
            window.cancelAnimationFrame(scrollTrackingRafRef.current);
        }

        scrollTrackingRafRef.current = window.requestAnimationFrame(() =>
        {
            const container = pdfContainerRef.current;
            if (!container) return;

            const containerRect = container.getBoundingClientRect();
            let nearestPage = currentPage;
            let nearestDistance = Number.POSITIVE_INFINITY;

            pageElementsRef.current.forEach((element, pageNumber) =>
            {
                const distance = Math.abs(element.getBoundingClientRect().top - containerRect.top);
                if (distance < nearestDistance)
                {
                    nearestDistance = distance;
                    nearestPage = pageNumber;
                }
            });

            if (nearestPage !== currentPage)
            {
                setCurrentPage(nearestPage);
                if (!isEditingPageInput)
                {
                    setPageInput(String(nearestPage));
                }
            }
        });
    };

    return {
        pdfContainerRef,
        numPages,
        currentPage,
        pageInput,
        visiblePages,
        registerPageRef,
        resetToFirstPage,
        scrollCurrentPageIntoView,
        onDocumentLoadSuccess,
        goToPage,
        goToNextPage: () => goToPage(currentPage + 1),
        goToPreviousPage: () => goToPage(currentPage - 1),
        handlePageInputChange,
        commitPageInput,
        setIsEditingPageInput,
        handlePdfScroll,
    };
};

export type PreviewPageNavigation = ReturnType<typeof usePreviewPageNavigation>;
