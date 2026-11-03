import React, {useEffect, useImperativeHandle} from 'react';
import {pdfjs} from 'react-pdf';
import "react-pdf/dist/Page/AnnotationLayer.css";
import "react-pdf/dist/Page/TextLayer.css";
import {mergeClasses} from "@fluentui/react-components";
import {DocumentDetailedDto, ExchangeDetailedDto} from "../../../models/models";
import {useExchangeDocumentPreviewerStyles} from "./ExchangeDocumentPreviewerStyles";
import {useIsMobile} from "../../../../utils/useMediaQuery.ts";
import {useAuth} from "../../../../context/AuthContext.tsx";
import ExchangeDocumentNotesPanel from "./exchange-document-notes-panel/ExchangeDocumentNotesPanel.tsx";
import {useDocumentCommentUnreadCount} from "./useDocumentCommentUnreadCount.ts";
import {resolvePreviewDocumentAccess} from "./previewDocumentAccess.ts";
import {usePreviewDocument} from "./usePreviewDocument.ts";
import {usePreviewEnlargement} from "./usePreviewEnlargement.ts";
import {usePreviewZoom} from "./usePreviewZoom.ts";
import {usePreviewContainerWidth} from "./usePreviewContainerWidth.ts";
import {usePreviewPageLayout} from "./usePreviewPageLayout.ts";
import {usePreviewDownloads} from "./usePreviewDownloads.ts";
import EnlargedPreviewHeader from "./enlarged-preview-header/EnlargedPreviewHeader.tsx";
import PreviewInlineControlsBar from "./preview-inline-controls-bar/PreviewInlineControlsBar.tsx";
import PreviewScrollPane from "./preview-scroll-pane/PreviewScrollPane.tsx";

pdfjs.GlobalWorkerOptions.workerSrc = `https://cdnjs.cloudflare.com/ajax/libs/pdf.js/${pdfjs.version}/pdf.worker.min.mjs`;

interface DocumentPreviewerProps
{
    document: DocumentDetailedDto;
    exchange: ExchangeDetailedDto;
    canUploadDocument?: boolean;
    onUploadDocument?: () => void;
    hideEnlarge?: boolean;
    overridePdfUrl?: string;
    documentVersionId?: string;
    onPageChange?: (pageNumber: number | undefined) => void;
}

export interface ExchangeDocumentPreviewerHandle
{
    goToPage: (pageNumber: number) => void;
}

const ExchangeDocumentPreviewer = React.forwardRef<ExchangeDocumentPreviewerHandle, DocumentPreviewerProps>((
    {
        document: exchangeDocument,
        exchange,
        canUploadDocument = false,
        onUploadDocument,
        overridePdfUrl,
        hideEnlarge = false,
        documentVersionId,
        onPageChange,
    }, ref) =>
{
    const styles = useExchangeDocumentPreviewerStyles();
    const isMobile = useIsMobile();
    const {appUser} = useAuth();
    const access = resolvePreviewDocumentAccess(exchange, exchangeDocument, appUser);
    const preview = usePreviewDocument(exchange.id, exchangeDocument, overridePdfUrl, access.downloadAllowed);
    const {navigation, pdfUrl, previewError} = preview;
    const {goToPage, numPages, currentPage} = navigation;
    const enlargement = usePreviewEnlargement(navigation.scrollCurrentPageIntoView);
    const {isEnlarged, isFullscreen, isClosingEnlarged, isNotesPanelOpen} = enlargement;
    const zoom = usePreviewZoom(isEnlarged);
    const containerWidth = usePreviewContainerWidth(navigation.pdfContainerRef, pdfUrl, isEnlarged, isFullscreen);
    const layout = usePreviewPageLayout(navigation.pdfContainerRef, isMobile, containerWidth, zoom.scale);
    const downloads = usePreviewDownloads(exchange.id, exchangeDocument);
    const unreadCommentCount = useDocumentCommentUnreadCount(exchange.id, exchangeDocument.id, isNotesPanelOpen);

    useImperativeHandle(ref, () => ({goToPage}), [goToPage]);

    useEffect(() =>
    {
        const hasPaginatedPreview = !!pdfUrl && !previewError && numPages > 0;
        onPageChange?.(hasPaginatedPreview ? currentPage : undefined);
    }, [onPageChange, pdfUrl, previewError, numPages, currentPage]);

    const sectionClassName = mergeClasses(
        isEnlarged ? styles.enlargedPreviewContainer : styles.previewContainer,
        isEnlarged && isClosingEnlarged && styles.enlargedPreviewContainerClosing,
        isFullscreen && styles.fullscreenPreviewContainer,
    );

    const documentShellClassName = isEnlarged
        ? mergeClasses(styles.enlargedPdfDocumentContainer, isClosingEnlarged && styles.enlargedPdfDocumentContainerClosing)
        : undefined;

    const scrollPane = (
        <PreviewScrollPane isEnlarged={isEnlarged}
                           exchangeDocument={exchangeDocument}
                           access={access}
                           preview={preview}
                           layout={layout}
                           downloads={downloads}
                           canUploadDocument={canUploadDocument}
                           onUploadDocument={onUploadDocument}/>
    );

    return (
        <section ref={enlargement.sectionRef}
                 className={sectionClassName}
                 id={"enlargedPreviewContainer"}>
            {isEnlarged && (
                <EnlargedPreviewHeader exchangeName={exchange.name}
                                       documentTitle={exchangeDocument.title}
                                       isMobile={isMobile}
                                       unreadCommentCount={unreadCommentCount}
                                       navigation={navigation}
                                       zoom={zoom}
                                       enlargement={enlargement}/>
            )}

            {isEnlarged ? (
                <div className={documentShellClassName}
                     id="pdfDocumentContainer">
                    <div className={styles.enlargedReaderLayout}
                         id="exchange-document-preview-reader-layout">
                        {scrollPane}
                        {isNotesPanelOpen && (
                            <ExchangeDocumentNotesPanel exchangeId={exchange.id}
                                                        exchangeDocument={exchangeDocument}
                                                        onClose={enlargement.closeNotesPanel}
                                                        pageNumber={currentPage}
                                                        documentVersionId={documentVersionId}
                                                        onNavigateToPage={goToPage}/>
                        )}
                    </div>
                </div>
            ) : (
                <>
                    {scrollPane}
                    {!!pdfUrl && !previewError && (
                        <PreviewInlineControlsBar navigation={navigation}
                                                  zoom={zoom}
                                                  isMobile={isMobile}
                                                  containerWidth={containerWidth}
                                                  hideEnlarge={hideEnlarge}
                                                  onToggleFullscreen={enlargement.toggleFullscreen}
                                                  onToggleEnlarge={enlargement.toggleEnlarge}/>
                    )}
                </>
            )}
        </section>
    );
});

ExchangeDocumentPreviewer.displayName = "ExchangeDocumentPreviewer";

export default ExchangeDocumentPreviewer;
