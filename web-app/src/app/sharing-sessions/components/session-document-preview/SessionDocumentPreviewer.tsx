import React, {useEffect, useRef, useState} from 'react';
import {Document, Page, pdfjs} from 'react-pdf';
import "react-pdf/dist/esm/Page/AnnotationLayer.css";
import "react-pdf/dist/esm/Page/TextLayer.css";
import {Button, Divider, Input, mergeClasses, Spinner, Text, Tooltip} from "@fluentui/react-components";
import {
    downloadPreviewPDFSharingSessionDocument,
    downloadSharingSessionDocument
} from "../../../../services/sharingSessionApi";
import {
    CollapseIcon,
    ExpandIcon,
    FirstPageIcon,
    LastPageIcon,
    NextPageIcon,
    PreviousPageIcon,
    ResetZoomIcon,
    ZoomInIcon,
    ZoomOutIcon
} from "../../../components/IconBundles.tsx";
import {DocumentDetailedDto, SharingSessionDetailedDto} from "../../../models/models";
import {useSessionDocumentPreviewerStyles} from "./SessionDocumentPreviewerStyles";

pdfjs.GlobalWorkerOptions.workerSrc = `https://cdnjs.cloudflare.com/ajax/libs/pdf.js/${pdfjs.version}/pdf.worker.min.mjs`;

interface DocumentPreviewerProps
{
    document: DocumentDetailedDto;
    session: SharingSessionDetailedDto;
    canUploadDocument?: boolean;
    onUploadDocument?: () => void;
}

const ENABLE_ENLARGED_THUMBNAIL_SIDEBAR = false;
const INLINE_ZOOM_STORAGE_KEY = 'sharingSessions.preview.zoom.inline';
const ENLARGED_ZOOM_STORAGE_KEY = 'sharingSessions.preview.zoom.enlarged';
const DEFAULT_ZOOM_LEVEL = 1.0;
const MIN_ZOOM_LEVEL = 0.5;
const MAX_ZOOM_LEVEL = 2.0;

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

const SessionDocumentPreviewer: React.FC<DocumentPreviewerProps> = (
    {
        document: sessionDocument,
        session,
        canUploadDocument = false,
        onUploadDocument,
    }) =>
{
    const styles = useSessionDocumentPreviewerStyles();
    const pdfContainerRef = useRef<HTMLDivElement>(null);
    const [pdfUrl, setPdfUrl] = useState<string | null>(null);
    const [numPages, setNumPages] = useState<number>(0);
    const [currentPage, setCurrentPage] = useState<number>(1);
    const [pageInput, setPageInput] = useState<string>('1');
    const [isEnlarged, setIsEnlarged] = useState<boolean>(false);
    const [isClosingEnlarged, setIsClosingEnlarged] = useState<boolean>(false);
    const [inlineZoomLevel, setInlineZoomLevel] = useState<number>(() => readZoomPreference(INLINE_ZOOM_STORAGE_KEY));
    const [enlargedZoomLevel, setEnlargedZoomLevel] = useState<number>(() => readZoomPreference(ENLARGED_ZOOM_STORAGE_KEY));
    const [previewError, setPreviewError] = useState<string | null>(null);
    const [downloadingOriginal, setDownloadingOriginal] = useState<boolean>(false);
    const [isEditingPageInput, setIsEditingPageInput] = useState<boolean>(false);
    const closeAnimationTimeoutRef = useRef<number | null>(null);
    const scrollTrackingRafRef = useRef<number | null>(null);
    const programmaticScrollTimeoutRef = useRef<number | null>(null);
    const isProgrammaticScrollRef = useRef(false);
    const zoomLevel = isEnlarged ? enlargedZoomLevel : inlineZoomLevel;

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
        };
    }, []);

    useEffect(() =>
    {
        const fetchDocument = async () =>
        {
            if (sessionDocument?.uploadDate)
            {
                setPreviewError(null);
                setPdfUrl(null);
                try
                {
                    const response = await downloadPreviewPDFSharingSessionDocument(session.id, sessionDocument.id);
                    const blob = new Blob([response as Blob], {type: 'application/pdf'});
                    const url = URL.createObjectURL(blob);
                    setPdfUrl(url);
                }
                catch (error: unknown)
                {
                    console.error("Error downloading document:", error);
                    const message = (typeof error === 'object' && error && 'message' in error)
                        ? String((error as { message?: unknown }).message)
                        : "Preview is unavailable for this document. You can still download the original file.";
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
    }, [sessionDocument, session.id]);

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

    const handleDownloadOriginal = async () =>
    {
        if (!sessionDocument?.id) return;
        setDownloadingOriginal(true);
        try
        {
            const blob = await downloadSharingSessionDocument(session.id, sessionDocument.id) as Blob;
            const url = URL.createObjectURL(blob);
            const link = window.document.createElement('a');
            link.href = url;
            link.download = sessionDocument.title || 'document';
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

    const onDocumentLoadSuccess = ({numPages}: { numPages: number }) =>
    {
        setNumPages(numPages);
        setCurrentPage(1);
        setPageInput('1');
    };

    const goToPage = (pageNumber: number) =>
    {
        if (pageNumber >= 1 && pageNumber <= numPages)
        {
            const pageElement = pdfContainerRef.current?.querySelector(`[data-page-number="${pageNumber}"]`);

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
                pageElement.scrollIntoView({behavior: 'smooth'});
            }

            setCurrentPage(pageNumber);
            setPageInput(String(pageNumber));
        }
    };

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

            const pageElements = Array.from(container.querySelectorAll('[data-page-number]')) as HTMLElement[];
            if (pageElements.length === 0) return;

            const containerRect = container.getBoundingClientRect();
            let nearestPage = currentPage;
            let nearestDistance = Number.POSITIVE_INFINITY;

            for (const element of pageElements)
            {
                const rect = element.getBoundingClientRect();
                const distance = Math.abs(rect.top - containerRect.top);
                if (distance < nearestDistance)
                {
                    nearestDistance = distance;
                    const pageNumber = Number(element.getAttribute('data-page-number') || '1');
                    nearestPage = pageNumber;
                }
            }

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

    const handleNextPage = () =>
    {
        goToPage(currentPage + 1);
    };

    const handlePreviousPage = () =>
    {
        goToPage(currentPage - 1);
    };

    const toggleEnlarge = () =>
    {
        if (isEnlarged)
        {
            if (isClosingEnlarged) return;
            setIsClosingEnlarged(true);
            closeAnimationTimeoutRef.current = window.setTimeout(() =>
            {
                setIsEnlarged(false);
                setIsClosingEnlarged(false);
            }, 220);
            return;
        }

        setIsEnlarged(true);
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

    const handleZoomIn = () =>
    {
        setActiveZoomLevel(zoomLevel + 0.1);
    };

    const handleZoomOut = () =>
    {
        setActiveZoomLevel(zoomLevel - 0.1);
    };

    const handleResetZoom = () =>
    {
        setActiveZoomLevel(DEFAULT_ZOOM_LEVEL);
    };

    const renderMainDocumentContent = () =>
    {
        return (
            <>
                {pdfUrl && !previewError && (
                    <Document
                        className={isEnlarged ? styles.enlargedPdfDocument : styles.pdfDocument}
                        file={pdfUrl}
                        loading={
                            <div className={styles.pdfLoadingContainer}>
                                <Spinner size="small"/>
                                <Text size={300} className={styles.pdfLoadingText}>Loading PDF...</Text>
                            </div>
                        }
                        onLoadSuccess={onDocumentLoadSuccess}
                        onLoadError={(error) =>
                        {
                            console.error("Failed to load PDF:", error);
                            setPreviewError("The preview could not be rendered. You can still download the original file.");
                        }}
                    >
                        {numPages > 0 && (
                            <div className={styles.pdfPagesStack}>
                                {Array.from({length: numPages}, (_, index) =>
                                {
                                    const pageNumber = index + 1;
                                    return (
                                        <div key={`page-${pageNumber}`} data-page-number={pageNumber} className={styles.pdfPageItem}>
                                            <Page
                                                className={styles.pdfPageAnimated}
                                                pageNumber={pageNumber}
                                                scale={isEnlarged ? zoomLevel * 1.1 : zoomLevel}
                                            />
                                        </div>
                                    );
                                })}
                            </div>
                        )}
                    </Document>
                )}
                {previewError && (
                    <div style={{
                        display: 'flex',
                        flexDirection: 'column',
                        alignItems: 'center',
                        justifyContent: 'center',
                        gap: '12px',
                        padding: '32px',
                        textAlign: 'center'
                    }}>
                        <Text size={500} weight={"semibold"}>Preview unavailable</Text>
                        <Text size={300}>{previewError}</Text>
                        <Button appearance="primary"
                                id="session-document-preview-download-original"
                                shape="circular"
                                disabled={downloadingOriginal}
                                onClick={handleDownloadOriginal}>
                            {downloadingOriginal ? 'Preparing download...' : 'Download original'}
                        </Button>
                    </div>
                )}

                {!previewError && !pdfUrl && !sessionDocument?.uploadDate && (
                    <div className={styles.previewEmptyState}>
                        <Text size={500} weight={"semibold"}>No file uploaded yet</Text>
                        <Text size={300} className={styles.previewEmptySubText}>
                            Upload a file for <b>{sessionDocument.title}</b> to start previewing.
                        </Text>
                        {canUploadDocument && (
                            <Button id="session-document-preview-upload-empty-cta" appearance="primary" shape="circular" onClick={onUploadDocument}>
                                Upload document
                            </Button>
                        )}
                        {!canUploadDocument && (
                            <Text size={200} className={styles.previewEmptySubText}>
                                You do not have permission to upload this document.
                            </Text>
                        )}
                    </div>
                )}
            </>
        );
    };

    return (
        <section className={isEnlarged
            ? mergeClasses(styles.enlargedPreviewContainer, isClosingEnlarged && styles.enlargedPreviewContainerClosing)
            : styles.previewContainer}
                 id={"enlargedPreviewContainer"}>
            <div className={isEnlarged
                ? mergeClasses(styles.enlargedPreviewHeader, isClosingEnlarged && styles.enlargedPreviewHeaderClosing)
                : styles.previewHeader}>
                {isEnlarged && (
                    <div className={styles.documentName}>
                        <Text size={200}>{session.sessionName}</Text>
                        <Text size={500}>{sessionDocument.title}</Text>
                    </div>
                )}
                <div className={isEnlarged ? styles.enlargedPreviewHeaderActions : styles.previewHeaderActions}>
                    {!isEnlarged && (
                        <>
                            <Button onClick={toggleEnlarge}
                                    id="session-document-preview-expand"
                                    appearance="transparent"
                                    icon={isEnlarged ? <CollapseIcon/> : <ExpandIcon/>}/>

                            <Divider vertical style={{height: "100%"}}/>

                            <Button onClick={handleZoomIn}
                                    id="session-document-preview-zoom-in-inline"
                                    appearance="transparent"
                                    icon={<ZoomInIcon/>}/>

                            <Tooltip content="Click to reset" relationship="description">
                                <Button onClick={handleResetZoom}
                                        id="session-document-preview-zoom-reset-inline"
                                        icon={<ResetZoomIcon/>}
                                        shape={"circular"}
                                        appearance="outline">
                                    {Math.round(zoomLevel * 100)}%
                                </Button>
                            </Tooltip>

                            <Button onClick={handleZoomOut}
                                    id="session-document-preview-zoom-out-inline"
                                    appearance="transparent"
                                    icon={<ZoomOutIcon/>}/>

                            <Divider vertical style={{height: "100%"}}/>
                        </>
                    )}

                    <div className={styles.pagesInputContainer}>
                        <Button onClick={() => goToPage(1)}
                                id="session-document-preview-page-first"
                                icon={<FirstPageIcon/>}
                                disabled={numPages === 0}
                                appearance="transparent"/>

                        <Button onClick={handlePreviousPage}
                                id="session-document-preview-page-previous"
                                appearance="transparent"
                                disabled={numPages === 0 || currentPage <= 1}
                                icon={<PreviousPageIcon/>}/>

                        <Input
                            id="session-document-preview-page-input"
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

                        <Button onClick={handleNextPage}
                                id="session-document-preview-page-next"
                                appearance="transparent"
                                disabled={numPages === 0 || currentPage >= numPages}
                                icon={<NextPageIcon/>}/>

                        <Button onClick={() => goToPage(numPages)}
                                id="session-document-preview-page-last"
                                icon={<LastPageIcon/>}
                                disabled={numPages === 0}
                                appearance="transparent"/>
                    </div>

                    {isEnlarged && (
                        <>
                            <Divider vertical style={{height: "100%"}}/>

                            <Button onClick={handleZoomOut}
                                    id="session-document-preview-zoom-out-enlarged"
                                    appearance="transparent"
                                    icon={<ZoomOutIcon/>}/>

                            <Tooltip content="Click to reset"
                                     relationship="description">
                                <Button onClick={handleResetZoom}
                                        id="session-document-preview-zoom-reset-enlarged"
                                        icon={<ResetZoomIcon/>}
                                        shape={"circular"}
                                        appearance="outline">
                                    {Math.round(zoomLevel * 100)}%
                                </Button>
                            </Tooltip>

                            <Button onClick={handleZoomIn}
                                    id="session-document-preview-zoom-in-enlarged"
                                    appearance="transparent"
                                    icon={<ZoomInIcon/>}/>

                            <Divider vertical style={{height: "100%"}}/>

                            <Tooltip content="Exit" relationship="description">
                                <Button onClick={toggleEnlarge}
                                        id="session-document-preview-exit-enlarged"
                                        appearance="transparent"
                                        icon={isEnlarged ? <CollapseIcon/> : <ExpandIcon/>}/>
                            </Tooltip>
                        </>
                    )}
                </div>
            </div>

            {isEnlarged ? (
                <div
                    className={mergeClasses(styles.enlargedPdfDocumentContainer, isClosingEnlarged && styles.enlargedPdfDocumentContainerClosing)}
                    id="pdfDocumentContainer">
                    <div className={styles.enlargedReaderLayout} id="session-document-preview-reader-layout">
                        {ENABLE_ENLARGED_THUMBNAIL_SIDEBAR && (
                            <aside className={styles.thumbnailSidebar} id="session-document-preview-thumbnail-sidebar">
                                {pdfUrl && !previewError && numPages > 0 && (
                                    <Document file={pdfUrl}>
                                        {Array.from({length: numPages}, (_, index) =>
                                        {
                                            const pageNumber = index + 1;
                                            return (
                                                <button
                                                    id={`session-document-preview-thumbnail-${pageNumber}`}
                                                    key={`thumb-${pageNumber}`}
                                                    type="button"
                                                    className={mergeClasses(
                                                        styles.thumbnailPageButton,
                                                        currentPage === pageNumber && styles.thumbnailPageButtonActive,
                                                    )}
                                                    onClick={() => goToPage(pageNumber)}>
                                                    <div className={styles.thumbnailPagePreview}>
                                                        <Page
                                                            pageNumber={pageNumber}
                                                            width={130}
                                                            renderTextLayer={false}
                                                            renderAnnotationLayer={false}
                                                        />
                                                    </div>
                                                    <Text size={200} className={styles.thumbnailPageNumber}>Page {pageNumber}</Text>
                                                </button>
                                            );
                                        })}
                                    </Document>
                                )}
                                {!previewError && pdfUrl && numPages === 0 && (
                                    <Text size={200} className={styles.thumbnailSidebarEmpty}>Preparing page previews...</Text>
                                )}
                                {!previewError && !pdfUrl && (
                                    <Text size={200} className={styles.thumbnailSidebarEmpty}>No page previews yet.</Text>
                                )}
                            </aside>
                        )}
                        <div
                            id="session-document-preview-main-panel"
                            ref={pdfContainerRef}
                            onScroll={handlePdfScroll}
                            className={styles.mainDocumentPane}>
                            {renderMainDocumentContent()}
                        </div>
                    </div>
                </div>
            ) : (
                <div
                    className={styles.pdfDocumentContainer}
                    id="pdfDocumentContainer"
                    ref={pdfContainerRef}
                    onScroll={handlePdfScroll}>
                    {renderMainDocumentContent()}
                </div>
            )}
        </section>
    );
};

export default SessionDocumentPreviewer;
