import React, {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import {Document, Page, pdfjs} from 'react-pdf';
import "react-pdf/dist/esm/Page/AnnotationLayer.css";
import "react-pdf/dist/esm/Page/TextLayer.css";
import {Button, Divider, Input, mergeClasses, Spinner, Text, Tooltip} from "@fluentui/react-components";
import {
    downloadPreviewPDFExchangeDocument,
    downloadExchangeDocument
} from "../../../../services/exchangeApi";
import {
    CollapseIcon,
    ExpandIcon,
    FirstPageIcon,
    FullScreenEnterIcon,
    FullScreenExitIcon,
    LastPageIcon,
    NextPageIcon,
    PreviousPageIcon,
    ResetZoomIcon,
    ZoomInIcon,
    ZoomOutIcon
} from "../../../components/IconBundles.tsx";
import {DocumentDetailedDto, ExchangeDetailedDto, ExchangeStatus} from "../../../models/models";
import {useExchangeDocumentPreviewerStyles} from "./ExchangeDocumentPreviewerStyles";
import {useIsMobile} from "../../../../utils/useMediaQuery.ts";
import {useAuth} from "../../../../context/AuthContext.tsx";

pdfjs.GlobalWorkerOptions.workerSrc = `https://cdnjs.cloudflare.com/ajax/libs/pdf.js/${pdfjs.version}/pdf.worker.min.mjs`;

interface DocumentPreviewerProps
{
    document: DocumentDetailedDto;
    exchange: ExchangeDetailedDto;
    canUploadDocument?: boolean;
    onUploadDocument?: () => void;
    /** When true the Enlarge button is hidden (e.g. when already rendered inside a large dialog). */
    hideEnlarge?: boolean;
}

const INLINE_ZOOM_STORAGE_KEY = 'exchanges.preview.zoom.inline';
const ENLARGED_ZOOM_STORAGE_KEY = 'exchanges.preview.zoom.enlarged';
const DEFAULT_ZOOM_LEVEL = 1.0;
const MIN_ZOOM_LEVEL = 0.5;
const MAX_ZOOM_LEVEL = 4.0;

// Default US-letter page dimensions used as a placeholder size until a page reports
// its actual size. This keeps the scroll height stable so virtualization works smoothly.
const DEFAULT_PAGE_WIDTH = 612;
const DEFAULT_PAGE_HEIGHT = 792;

// Virtualization buffer: number of pages above/below the viewport to keep rendered.
const VIRTUALIZATION_BUFFER_PAGES = 2;

interface PageDimensions
{
    width: number;
    height: number;
}

const clampZoom = (value: number): number =>
{
    return Math.min(Math.max(value, MIN_ZOOM_LEVEL), MAX_ZOOM_LEVEL);
};

const readZoomPreference = (storageKey: string, fallback: number = DEFAULT_ZOOM_LEVEL): number =>
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

const ExchangeDocumentPreviewer: React.FC<DocumentPreviewerProps> = (
    {
        document: exchangeDocument,
        exchange,
        canUploadDocument = false,
        onUploadDocument,
        overridePdfUrl,
        hideEnlarge = false,
    }) =>
{
    const styles = useExchangeDocumentPreviewerStyles();
    const isMobile = useIsMobile();
    const {appUser} = useAuth();
    const watermarkEnabled = !!exchange?.watermark;
    const downloadAllowed = exchange?.allowDocumentDownload !== false;
    const canDownloadOriginal =
        downloadAllowed &&
        (!exchange?.allowedDownloadFormats ||
         exchange.allowedDownloadFormats.includes(exchangeDocument?.type ?? ''));
    const canDownloadAsPdf =
        downloadAllowed && exchangeDocument?.type !== 'PDF';
    const watermarkText = (appUser?.email || 'CONFIDENTIAL').toUpperCase();
    const sectionRef = useRef<HTMLElement>(null);
    const pdfContainerRef = useRef<HTMLDivElement>(null);
    const pageElementsRef = useRef<Map<number, HTMLDivElement>>(new Map());
    const intersectionObserverRef = useRef<IntersectionObserver | null>(null);
    const [pdfUrl, setPdfUrl] = useState<string | null>(null);
    const [numPages, setNumPages] = useState<number>(0);
    const [currentPage, setCurrentPage] = useState<number>(1);
    const [pageInput, setPageInput] = useState<string>('1');
    const [isEnlarged, setIsEnlarged] = useState<boolean>(false);
    const [isFullscreen, setIsFullscreen] = useState<boolean>(false);
    const [isClosingEnlarged, setIsClosingEnlarged] = useState<boolean>(false);
    const [inlineZoomLevel, setInlineZoomLevel] = useState<number>(() => readZoomPreference(INLINE_ZOOM_STORAGE_KEY));
    const [enlargedZoomLevel, setEnlargedZoomLevel] = useState<number>(() => readZoomPreference(ENLARGED_ZOOM_STORAGE_KEY));
    const [previewError, setPreviewError] = useState<string | null>(null);
    const [downloadingOriginal, setDownloadingOriginal] = useState<boolean>(false);
    const [downloadingPdf, setDownloadingPdf] = useState<boolean>(false);
    const [isEditingPageInput, setIsEditingPageInput] = useState<boolean>(false);
    const [visiblePages, setVisiblePages] = useState<Set<number>>(() => new Set([1]));
    // Width of the scroll container's content box, used to auto-fit the
    // PDF page to the available width on mobile. Updated by a
    // ResizeObserver (so it stays correct across orientation changes,
    // collapsing the side panel, etc).
    const [containerWidth, setContainerWidth] = useState<number>(0);
    // Single representative page size; PDFs in the same document virtually always share dimensions.
    const [pageDimensions, setPageDimensions] = useState<PageDimensions>({
        width: DEFAULT_PAGE_WIDTH,
        height: DEFAULT_PAGE_HEIGHT,
    });

    const closeAnimationTimeoutRef = useRef<number | null>(null);
    const scrollTrackingRafRef = useRef<number | null>(null);
    const programmaticScrollTimeoutRef = useRef<number | null>(null);
    const isProgrammaticScrollRef = useRef(false);
    // Tracks which PDF URL we last fired the "reset to page 1" logic for. When the
    // <Document> remounts (e.g. when toggling enlarged/fullscreen, which changes the
    // surrounding JSX tree) onDocumentLoadSuccess fires again with the *same* URL exch-
    // we must NOT reset the user's current page in that case.
    const lastLoadedUrlRef = useRef<string | null>(null);

    const scale = isEnlarged ? enlargedZoomLevel : inlineZoomLevel;

    // Track the scroll container's inner width so we can compute a
    // "fit to width" page size for mobile (where we hide the zoom
    // controls and the PDF must render at the viewport width to be
    // readable without horizontal scrolling).
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
    }, [pdfUrl, isEnlarged, isFullscreen]);

    // Cleanup all timers on unmount.
    useEffect(() =>
    {
        return () =>
        {
            if (closeAnimationTimeoutRef.current)
            {
                window.clearTimeout(closeAnimationTimeoutRef.current);
            }
            if (scrollTrackingRafRef.current)
            {
                window.cancelAnimationFrame(scrollTrackingRafRef.current);
            }
            if (programmaticScrollTimeoutRef.current)
            {
                window.clearTimeout(programmaticScrollTimeoutRef.current);
            }
            intersectionObserverRef.current?.disconnect();
        };
    }, []);

    // Fetch / refresh the PDF blob whenever the underlying document changes.
    useEffect(() =>
    {
        // When a pre-fetched URL is provided (e.g. for a specific version), use it directly
        // and skip the server fetch. The caller is responsible for revoking the URL.
        if (overridePdfUrl)
        {
            setPdfUrl(overridePdfUrl);
            setPreviewError(null);
            setCurrentPage(1);
            setPageInput('1');
            setVisiblePages(new Set([1]));
            lastLoadedUrlRef.current = null;
            return;
        }

        let revokedUrl: string | null = null;
        const fetchDocument = async () =>
        {
            if (exchangeDocument?.uploadDate)
            {
                setPreviewError(null);
                setPdfUrl(null);
                try
                {
                    const response = await downloadPreviewPDFExchangeDocument(exchange.id, exchangeDocument.id);
                    const blob = new Blob([response as Blob], {type: 'application/pdf'});
                    const url = URL.createObjectURL(blob);
                    revokedUrl = url;
                    setPdfUrl(url);
                }
                catch (error: unknown)
                {
                    console.error("Error downloading document:", error);
                    const dlMsg = downloadAllowed ? "You can still download the original file" : ''

                    const message = (typeof error === 'object' && error && 'message' in error)
                        ? String((error as { message?: unknown }).message)
                        : `Preview is unavailable for this document. ${dlMsg}`;
                    setPreviewError(message);
                }
            }
            else
            {
                setPreviewError(null);
                setPdfUrl(null);
            }
        };
        fetchDocument();
        setCurrentPage(1);
        setPageInput('1');
        setVisiblePages(new Set([1]));
        // Reset the "last loaded URL" guard so the new document is treated as fresh
        // (and resets to page 1) rather than being mistaken for a remount of the previous one.
        lastLoadedUrlRef.current = null;
        return () =>
        {
            if (revokedUrl)
            {
                URL.revokeObjectURL(revokedUrl);
            }
        };
    }, [exchangeDocument, exchange.id, overridePdfUrl]);

    useEffect(() =>
    {
        if (typeof window === 'undefined') return;
        window.localStorage.setItem(INLINE_ZOOM_STORAGE_KEY, String(inlineZoomLevel));
    }, [inlineZoomLevel]);

    useEffect(() =>
    {
        if (typeof window === 'undefined') return;
        window.localStorage.setItem(ENLARGED_ZOOM_STORAGE_KEY, String(enlargedZoomLevel));
    }, [enlargedZoomLevel]);

    // Track real browser fullscreen state so we keep the UI in sync if the user presses ESC.
    useEffect(() =>
    {
        const handleFullscreenChange = () =>
        {
            const active = window.document.fullscreenElement === sectionRef.current;
            setIsFullscreen(active);
            if (active)
            {
                setIsEnlarged(true);
            }
        };
        window.document.addEventListener('fullscreenchange', handleFullscreenChange);
        return () =>
        {
            window.document.removeEventListener('fullscreenchange', handleFullscreenChange);
        };
    }, []);

    // -------------------------------------------------------------------------
    // Virtualization: only render <Page> components for pages near the viewport.
    // We use a single IntersectionObserver attached to the scroll container,
    // observing each page placeholder. When a placeholder intersects, we mark
    // it (and a small buffer) as visible.
    // -------------------------------------------------------------------------
    useEffect(() =>
    {
        intersectionObserverRef.current?.disconnect();

        const root = pdfContainerRef.current;
        if (!root || numPages === 0)
        {
            return;
        }

        const observer = new IntersectionObserver(
            (entries) =>
            {
                setVisiblePages((previous) =>
                {
                    const next = new Set(previous);
                    let mutated = false;
                    for (const entry of entries)
                    {
                        const target = entry.target as HTMLElement;
                        const pageNumber = Number(target.getAttribute('data-page-number'));
                        if (!pageNumber) continue;

                        if (entry.isIntersecting)
                        {
                            for (let p = Math.max(1, pageNumber - VIRTUALIZATION_BUFFER_PAGES);
                                 p <= Math.min(numPages, pageNumber + VIRTUALIZATION_BUFFER_PAGES);
                                 p++)
                            {
                                if (!next.has(p))
                                {
                                    next.add(p);
                                    mutated = true;
                                }
                            }
                        }
                        else
                        {
                            // Keep rendered pages within the buffer range of currently-intersecting
                            // pages; trim ones that are far away to free canvas memory.
                            if (next.has(pageNumber)
                                && Math.abs(pageNumber - currentPage) > VIRTUALIZATION_BUFFER_PAGES + 2)
                            {
                                next.delete(pageNumber);
                                mutated = true;
                            }
                        }
                    }
                    return mutated ? next : previous;
                });
            },
            {
                root,
                // Pre-load pages that are within ~1 viewport above/below.
                rootMargin: '200% 0px 200% 0px',
                threshold: 0,
            }
        );

        intersectionObserverRef.current = observer;
        pageElementsRef.current.forEach((el) => observer.observe(el));

        return () =>
        {
            observer.disconnect();
        };
    }, [numPages, pdfUrl, currentPage]);

    const registerPageRef = useCallback((pageNumber: number, element: HTMLDivElement | null) =>
    {
        const existing = pageElementsRef.current.get(pageNumber);
        if (existing && existing !== element)
        {
            intersectionObserverRef.current?.unobserve(existing);
            pageElementsRef.current.delete(pageNumber);
        }
        if (element)
        {
            pageElementsRef.current.set(pageNumber, element);
            intersectionObserverRef.current?.observe(element);
        }
    }, []);

    const handleDownloadOriginal = async () =>
    {
        if (!exchangeDocument?.id) return;
        setDownloadingOriginal(true);
        try
        {
            const blob = await downloadExchangeDocument(exchange.id, exchangeDocument.id) as Blob;
            const url = URL.createObjectURL(blob);
            const link = window.document.createElement('a');
            link.href = url;
            link.download = exchangeDocument.title || 'document';
            window.document.body.appendChild(link);
            link.click();
            window.document.body.removeChild(link);
            URL.revokeObjectURL(url);
        }
        catch (err)
        {
            console.error("Failed to download original document:", err);
        }
        finally
        {
            setDownloadingOriginal(false);
        }
    };

    const handleDownloadAsPdf = async () =>
    {
        if (!exchangeDocument?.id) return;
        setDownloadingPdf(true);
        try
        {
            const blob = await downloadPreviewPDFExchangeDocument(exchange.id, exchangeDocument.id);
            const url = URL.createObjectURL(new Blob([blob as Blob], {type: 'application/pdf'}));
            const a = window.document.createElement('a');
            a.href = url;
            a.download = `${exchangeDocument?.title ?? 'document'}.pdf`;
            window.document.body.appendChild(a);
            a.click();
            window.document.body.removeChild(a);
            URL.revokeObjectURL(url);
        }
        catch (err)
        {
            console.error("Failed to download document as PDF:", err);
        }
        finally
        {
            setDownloadingPdf(false);
        }
    };

    const onDocumentLoadSuccess = ({numPages}: { numPages: number }) =>
    {
        setNumPages(numPages);

        // Only reset to page 1 when the underlying PDF actually changed. If onLoadSuccess
        // fires for the *same* URL (the <Document> was just unmounted/remounted because the
        // JSX tree changed when entering/leaving enlarged/fullscreen mode), preserve the
        // user's current page and re-anchor scroll to it.
        if (lastLoadedUrlRef.current !== pdfUrl)
        {
            lastLoadedUrlRef.current = pdfUrl;
            setCurrentPage(1);
            setPageInput('1');
            setVisiblePages(new Set([1]));
            return;
        }

        const restorePage = Math.min(Math.max(currentPage, 1), numPages);
        setVisiblePages((previous) =>
        {
            const next = new Set(previous);
            for (let p = Math.max(1, restorePage - VIRTUALIZATION_BUFFER_PAGES);
                 p <= Math.min(numPages, restorePage + VIRTUALIZATION_BUFFER_PAGES);
                 p++)
            {
                next.add(p);
            }
            return next;
        });

        // Wait two frames so the freshly-mounted page divs are in the DOM and have laid out
        // before we scroll. Suppress the scroll listener's "snap to nearest" while we move.
        isProgrammaticScrollRef.current = true;
        if (programmaticScrollTimeoutRef.current)
        {
            window.clearTimeout(programmaticScrollTimeoutRef.current);
        }
        programmaticScrollTimeoutRef.current = window.setTimeout(() =>
        {
            isProgrammaticScrollRef.current = false;
        }, 450);
        window.requestAnimationFrame(() =>
        {
            window.requestAnimationFrame(() =>
            {
                pageElementsRef.current.get(restorePage)?.scrollIntoView({block: 'start'});
            });
        });
    };

    const handlePageLoadSuccess = useCallback((pdfPage: { width: number; height: number }) =>
    {
        // Capture native (scale = 1.0) page dimensions once so all placeholders are correctly sized.
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

    const goToPage = useCallback((pageNumber: number) =>
    {
        if (pageNumber >= 1 && pageNumber <= numPages)
        {
            // Eagerly mark the target page as visible so it renders by the time we scroll there.
            setVisiblePages((previous) =>
            {
                const next = new Set(previous);
                for (let p = Math.max(1, pageNumber - VIRTUALIZATION_BUFFER_PAGES);
                     p <= Math.min(numPages, pageNumber + VIRTUALIZATION_BUFFER_PAGES);
                     p++)
                {
                    next.add(p);
                }
                return next;
            });

            const pageElement = pageElementsRef.current.get(pageNumber);

            if (pageElement)
            {
                isProgrammaticScrollRef.current = true;
                if (programmaticScrollTimeoutRef.current)
                {
                    window.clearTimeout(programmaticScrollTimeoutRef.current);
                }
                programmaticScrollTimeoutRef.current = window.setTimeout(() =>
                {
                    isProgrammaticScrollRef.current = false;
                }, 450);
                pageElement.scrollIntoView({behavior: 'smooth', block: 'start'});
            }

            setCurrentPage(pageNumber);
            setPageInput(String(pageNumber));
        }
    }, [numPages]);

    const handlePageInputChange = (event: React.ChangeEvent<HTMLInputElement>) =>
    {
        const next = event.target.value.replace(/[^0-9]/g, '');
        setPageInput(next);
    };

    const commitPageInput = () =>
    {
        const parsed = parseInt(pageInput, 10);
        if (Number.isNaN(parsed))
        {
            setPageInput(String(currentPage));
            return;
        }

        const boundedPage = Math.min(Math.max(parsed, 1), Math.max(numPages, 1));
        goToPage(boundedPage);
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
                const rect = element.getBoundingClientRect();
                const distance = Math.abs(rect.top - containerRect.top);
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

    const handleNextPage = () => goToPage(currentPage + 1);
    const handlePreviousPage = () => goToPage(currentPage - 1);

    const toggleEnlarge = () =>
    {
        if (isEnlarged)
        {
            if (isClosingEnlarged) return;
            // Exit browser fullscreen first if it was active.
            if (window.document.fullscreenElement)
            {
                window.document.exitFullscreen().catch(() => { /* noop */ });
            }
            setIsClosingEnlarged(true);
            closeAnimationTimeoutRef.current = window.setTimeout(() =>
            {
                setIsEnlarged(false);
                setIsClosingEnlarged(false);
                // Re-anchor scroll to the current page in the new layout on the next frame.
                window.requestAnimationFrame(() =>
                {
                    const el = pageElementsRef.current.get(currentPage);
                    el?.scrollIntoView({block: 'start'});
                });
            }, 220);
            return;
        }

        setIsEnlarged(true);
        // Re-anchor scroll to the current page after the enlarged layout mounts.
        window.requestAnimationFrame(() =>
        {
            window.requestAnimationFrame(() =>
            {
                const el = pageElementsRef.current.get(currentPage);
                el?.scrollIntoView({block: 'start'});
            });
        });
    };

    const toggleFullscreen = async () =>
    {
        const root = sectionRef.current;
        if (!root) return;

        try
        {
            if (window.document.fullscreenElement)
            {
                await window.document.exitFullscreen();
            }
            else
            {
                if (!isEnlarged)
                {
                    setIsEnlarged(true);
                }
                await root.requestFullscreen();
            }
        }
        catch (err)
        {
            console.warn('Fullscreen request failed:', err);
        }
    };

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

    const handleZoomIn = () => setActiveZoomLevel(scale + 0.1);
    const handleZoomOut = () => setActiveZoomLevel(scale - 0.1);
    const handleResetZoom = () => setActiveZoomLevel(DEFAULT_ZOOM_LEVEL);

    // On mobile we render Pages with an explicit pixel width matching
    // the scroll container so the PDF fits without horizontal scrolling.
    // The user can still pinch-zoom (touch-action allows it) and we hide
    // the toolbar zoom controls in that mode.
    const fitToWidth = isMobile && containerWidth > 0;
    const fitWidthPx = fitToWidth ? containerWidth : undefined;

    // Memoized scaled placeholder dimensions, so resizing doesn't re-create style objects per-page.
    const placeholderStyle = useMemo<React.CSSProperties>(() =>
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

    // Props passed to react-pdf's <Page>. When `width` is set the
    // library auto-computes scale, so we omit `scale` in that mode.
    const pagePresentationProps = fitToWidth
        ? {width: fitWidthPx}
        : {scale};

    const renderPdfBody = () =>
    {
        if (previewError)
        {
            return (
                <div className={styles.previewErrorContainer}>
                    <Text size={500} weight={"semibold"}>Preview unavailable</Text>
                    <Text size={300}>{previewError}</Text>
                    {canDownloadOriginal && (
                        <Button appearance="primary"
                                id="exchange-document-preview-download-original"
                                shape="circular"
                                disabled={downloadingOriginal}
                                onClick={handleDownloadOriginal}>
                            {downloadingOriginal ? 'Preparing download...' : 'Download original'}
                        </Button>
                    )}
                    {canDownloadAsPdf && (
                        <Button appearance="secondary"
                                id="exchange-document-preview-download-pdf"
                                shape="circular"
                                disabled={downloadingPdf}
                                onClick={handleDownloadAsPdf}>
                            {downloadingPdf ? 'Preparing PDF…' : 'Download as PDF'}
                        </Button>
                    )}
                    {!downloadAllowed && (
                        <Text size={200} italic>
                            Download is disabled for this exchange.
                        </Text>
                    )}
                </div>
            );
        }

        if (!pdfUrl && !exchangeDocument?.uploadDate)
        {
            const isArchivedExchange = exchange?.status === ExchangeStatus.ENDED
                || exchange?.status === ExchangeStatus.REJECTED
                || exchange?.status === ExchangeStatus.RESCINDED;

            if (isArchivedExchange)
            {
                return (
                    <div className={styles.previewEmptyState}>
                        <Text size={500} weight={"semibold"}>Document not yet uploaded</Text>
                        <Text size={300} className={styles.previewEmptySubText} align={"center"}>
                            <b>{exchangeDocument.title}</b> has no uploaded file, and this exchange is archived so no further uploads can be made.
                        </Text>
                    </div>
                );
            }

            return (
                <div className={styles.previewEmptyState} id={'preview-empty-state'}>
                    <Text size={500} weight={"semibold"}>No file uploaded yet</Text>
                    <Text size={300} className={styles.previewEmptySubText} align={"center"}>
                        Upload a file for <b>{exchangeDocument.title}</b> to start previewing.
                    </Text>
                    {canUploadDocument && (
                        <Button id="exchange-document-preview-upload-empty-cta" appearance="primary" shape="circular"
                                onClick={onUploadDocument}>
                            Upload document
                        </Button>
                    )}
                    {!canUploadDocument && (
                        <Text size={200} className={styles.previewEmptySubText}>
                            You do not have permission to upload this document.
                        </Text>
                    )}
                </div>
            );
        }

        if (!pdfUrl)
        {
            return (
                <div className={styles.pdfLoadingContainer}>
                    <Spinner size="small"/>
                    <Text size={300} className={styles.pdfLoadingText}>Loading Document</Text>
                </div>
            );
        }

        return (
            <div className={styles.pdfRelativeWrapper}>
            <Document
                className={styles.pdfDocument}
                file={pdfUrl}
                loading={
                    <div className={styles.pdfLoadingContainer}>
                        <Spinner size="small"/>
                        <Text size={300} className={styles.pdfLoadingText}>Loading Document</Text>
                    </div>
                }
                onLoadSuccess={onDocumentLoadSuccess}
                onLoadError={(error) =>
                {
                    console.error("Failed to load PDF:", error);
                    const dlMsg = downloadAllowed ? "You can still download the original file" : ''
                    setPreviewError(`The preview could not be rendered. ${dlMsg}`);
                }}
            >
                {numPages > 0 && (
                    <div className={styles.pdfPagesStack}>
                        {Array.from({length: numPages}, (_, index) =>
                        {
                            const pageNumber = index + 1;
                            const isVisible = visiblePages.has(pageNumber);
                            return (
                                <div key={`page-${pageNumber}`}
                                     data-page-number={pageNumber}
                                     ref={(el) => registerPageRef(pageNumber, el)}
                                     className={styles.pdfPageItem}>
                                    {isVisible ? (
                                        <Page
                                            className={styles.pdfPageAnimated}
                                            pageNumber={pageNumber}
                                            {...pagePresentationProps}
                                            onLoadSuccess={handlePageLoadSuccess}
                                            loading={
                                                <div className={styles.pdfPagePlaceholder} style={placeholderStyle}>
                                                    Loading page {pageNumber}…
                                                </div>
                                            }
                                        />
                                    ) : (
                                        // Off-screen page: still render as a <Page> so it stays
                                        // registered with react-pdf's LinkService (otherwise internal
                                        // PDF links / TOC clicks to this page would silently no-op).
                                        // `renderMode="none"` skips the expensive canvas paint, and we
                                        // disable text + annotation layers exch- the page becomes essentially
                                        // free while still being a proper navigation target.
                                        <Page
                                            pageNumber={pageNumber}
                                            {...pagePresentationProps}
                                            renderMode="none"
                                            renderTextLayer={false}
                                            renderAnnotationLayer={false}
                                            onLoadSuccess={handlePageLoadSuccess}
                                            loading={
                                                <div className={styles.pdfPagePlaceholder} style={placeholderStyle}>
                                                    Page {pageNumber}
                                                </div>
                                            }
                                        />
                                    )}
                                </div>
                            );
                        })}
                    </div>
                )}
            </Document>
            {watermarkEnabled && (
                <div
                    aria-hidden
                    className={styles.watermarkOverlay}
                >
                    <div className={styles.watermarkText}>
                        {watermarkText} · CONFIDENTIAL
                    </div>
                </div>
            )}
            </div>
        );
    };

    // Stable JSX structure: same DOM tree regardless of `isEnlarged`, only class names swap.
    // This keeps the <Document> and rendered <Page> canvases mounted across the transition
    // so we never see a blank flash when entering/leaving the enlarged/fullscreen view.
    const sectionClassName = mergeClasses(
        isEnlarged ? styles.enlargedPreviewContainer : styles.previewContainer,
        isEnlarged && isClosingEnlarged && styles.enlargedPreviewContainerClosing,
        isFullscreen && styles.fullscreenPreviewContainer,
    );

    const headerClassName = mergeClasses(
        isEnlarged ? styles.enlargedPreviewHeader : styles.previewHeader,
        isEnlarged && isClosingEnlarged && styles.enlargedPreviewHeaderClosing,
    );

    const documentShellClassName = isEnlarged
        ? mergeClasses(styles.enlargedPdfDocumentContainer, isClosingEnlarged && styles.enlargedPdfDocumentContainerClosing)
        : undefined;

    const scrollPaneClassName = isEnlarged ? styles.mainDocumentPane : styles.pdfDocumentContainer;

    // The actual scroll pane. Kept in a local variable so both render paths render
    // *the same* element (same ref, same children), maximising the chance React
    // will reconcile rather than remount when only the surrounding wrappers change.
    const scrollPane = (
        <div
            id="exchange-document-preview-main-panel"
            ref={pdfContainerRef}
            onScroll={handlePdfScroll}
            className={scrollPaneClassName}>
            {renderPdfBody()}
        </div>
    );

    return (
        <section ref={sectionRef} className={sectionClassName} id={"enlargedPreviewContainer"}>
            <div className={headerClassName}>
                {isEnlarged && (
                    <div className={styles.documentName}>
                        <Text size={200}>{exchange.name}</Text>
                        <Text size={500}>{exchangeDocument.title}</Text>
                    </div>
                )}
                <div className={isEnlarged ? styles.enlargedPreviewHeaderActions : styles.previewHeaderActions}>
                    {!isEnlarged && (
                        <>
                            {/*
                              Mobile (phones):
                              - Hide Fullscreen (most mobile browsers
                                require an explicit UA prompt to enter
                                fullscreen, and we already promote to
                                enlarged on tap).
                              - Show Enlarge instead - it gives a clean
                                in-app reader mode that uses the entire
                                viewport.
                              - Hide Zoom in / out / reset entirely; the
                                user can pinch-zoom the PDF directly via
                                the `touch-action` rule on the scroll
                                container.
                              Desktop/tablet keeps all controls.
                            */}
                            <Tooltip content="Enlarge" relationship="description">
                                <Button
                                    onClick={toggleEnlarge}
                                    id="exchange-document-preview-expand"
                                    appearance="transparent"
                                    shape={"circular"}
                                    style={hideEnlarge ? {display: 'none'} : undefined}
                                    icon={<ExpandIcon/>}/>
                            </Tooltip>

                            {!isMobile && (
                                <Tooltip content="Fullscreen" relationship="description">
                                    <Button
                                        onClick={toggleFullscreen}
                                        id="exchange-document-preview-fullscreen-inline"
                                        appearance="transparent"
                                        shape={"circular"}
                                        icon={<FullScreenEnterIcon/>}/>
                                </Tooltip>
                            )}

                            {!isMobile && (
                                <>
                                    <Divider vertical className={styles.dividerFullHeight}/>

                                    <Button
                                        onClick={handleZoomIn}
                                        id="exchange-document-preview-zoom-in-inline"
                                        appearance="transparent"
                                        shape={"circular"}
                                        icon={<ZoomInIcon/>}/>

                                    <Tooltip content="Click to reset" relationship="description">
                                        <Button onClick={handleResetZoom}
                                                id="exchange-document-preview-zoom-reset-inline"
                                                icon={<ResetZoomIcon/>}
                                                shape={"circular"}
                                                appearance="secondary">
                                            {Math.round(scale * 100)}%
                                        </Button>
                                    </Tooltip>

                                    <Button
                                        onClick={handleZoomOut}
                                        id="exchange-document-preview-zoom-out-inline"
                                        appearance="transparent"
                                        shape={"circular"}
                                        icon={<ZoomOutIcon/>}/>

                                    <Divider vertical className={styles.dividerFullHeight}/>
                                </>
                            )}
                        </>
                    )}

                    <div className={styles.pagesInputContainer}>
                        <Button
                            onClick={() => goToPage(1)}
                            id="exchange-document-preview-page-first"
                            icon={<FirstPageIcon/>}
                            disabled={numPages === 0}
                            appearance="transparent"
                            shape={"circular"}/>

                        <Button
                            onClick={handlePreviousPage}
                            id="exchange-document-preview-page-previous"
                            appearance="transparent"
                            shape={"circular"}
                            disabled={numPages === 0 || currentPage <= 1}
                            icon={<PreviousPageIcon/>}/>

                        <Input
                            id="exchange-document-preview-page-input"
                            type="text"
                            value={pageInput}
                            onChange={handlePageInputChange}
                            onFocus={() => setIsEditingPageInput(true)}
                            onBlur={() =>
                            {
                                setIsEditingPageInput(false);
                                commitPageInput();
                            }}
                            onKeyDown={(event) =>
                            {
                                if (event.key === 'Enter')
                                {
                                    setIsEditingPageInput(false);
                                    commitPageInput();
                                }
                            }}
                            className={styles.pagesInput}
                            contentAfter={<Text className={styles.pagesInputAfter}>{` / ${numPages}`}</Text>}
                        />

                        <Button
                            onClick={handleNextPage}
                            id="exchange-document-preview-page-next"
                            appearance="transparent"
                            shape={"circular"}
                            disabled={numPages === 0 || currentPage >= numPages}
                            icon={<NextPageIcon/>}/>

                        <Button
                            onClick={() => goToPage(numPages)}
                            id="exchange-document-preview-page-last"
                            icon={<LastPageIcon/>}
                            disabled={numPages === 0}
                            appearance="transparent"
                            shape={"circular"}/>
                    </div>

                    {isEnlarged && (
                        <>
                            {/*
                              Mobile enlarged view: match the inline
                              mobile toolbar - hide Zoom in/out/reset
                              (pinch to zoom) and Fullscreen (we never
                              entered it from mobile inline mode either,
                              so a toggle here would be confusing). Keep
                              only the Exit button so the user can
                              return to the document list view.
                            */}
                            {!isMobile && (
                                <>
                                    <Divider vertical className={styles.dividerFullHeight}/>

                                    <Button
                                        onClick={handleZoomOut}
                                        id="exchange-document-preview-zoom-out-enlarged"
                                        appearance="transparent"
                                        shape={"circular"}
                                        icon={<ZoomOutIcon/>}/>

                                    <Tooltip content="Click to reset"
                                             relationship="description">
                                        <Button onClick={handleResetZoom}
                                                id="exchange-document-preview-zoom-reset-enlarged"
                                                icon={<ResetZoomIcon/>}
                                                shape={"circular"}
                                                appearance="secondary">
                                            {Math.round(scale * 100)}%
                                        </Button>
                                    </Tooltip>

                                    <Button
                                        onClick={handleZoomIn}
                                        id="exchange-document-preview-zoom-in-enlarged"
                                        appearance="transparent"
                                        shape={"circular"}
                                        icon={<ZoomInIcon/>}/>

                                    <Divider vertical className={styles.dividerFullHeight}/>

                                    <Tooltip content={isFullscreen ? "Exit fullscreen" : "Fullscreen"} relationship="description">
                                        <Button
                                            onClick={toggleFullscreen}
                                            id="exchange-document-preview-fullscreen-enlarged"
                                            appearance="transparent"
                                            shape={"circular"}
                                            icon={isFullscreen ? <FullScreenExitIcon/> : <FullScreenEnterIcon/>}/>
                                    </Tooltip>
                                </>
                            )}

                            <Tooltip content="Exit" relationship="description">
                                <Button
                                    onClick={toggleEnlarge}
                                    id="exchange-document-preview-exit-enlarged"
                                    appearance="transparent"
                                    shape={"circular"}
                                    icon={<CollapseIcon/>}/>
                            </Tooltip>
                        </>
                    )}
                </div>
            </div>

            {/*
              * Two render paths so that inline mode mirrors the original (proven) DOM
              * layout exactly exch- a single scroll pane as a direct flex child of <section>.
              * The enlarged mode adds the reader-layout wrappers (which previously had a
              * thumbnail sidebar slot, now disabled). Toggling modes does briefly remount
              * the <Document>, but with virtualization only the visible page(s) re-render,
              * which is fast and barely noticeable.
              */}
            {isEnlarged ? (
                <div className={documentShellClassName} id="pdfDocumentContainer">
                    <div className={styles.enlargedReaderLayout}
                         id="exchange-document-preview-reader-layout">
                        {scrollPane}
                    </div>
                </div>
            ) : (
                scrollPane
            )}
        </section>
    );
};

export default ExchangeDocumentPreviewer;
