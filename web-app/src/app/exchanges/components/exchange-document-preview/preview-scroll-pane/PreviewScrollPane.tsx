import {DocumentDetailedDto} from "../../../../models/models";
import {PreviewDocumentAccess} from "../previewDocumentAccess.ts";
import {PreviewDocument} from "../usePreviewDocument.ts";
import {PreviewPageLayout} from "../usePreviewPageLayout.ts";
import {PreviewDownloads} from "../usePreviewDownloads.ts";
import PreviewUnavailableState from "../preview-unavailable-state/PreviewUnavailableState.tsx";
import PreviewEmptyState from "../preview-empty-state/PreviewEmptyState.tsx";
import PreviewLoadingIndicator from "../preview-loading-indicator/PreviewLoadingIndicator.tsx";
import PdfPageStack from "../pdf-page-stack/PdfPageStack.tsx";
import {usePreviewScrollPaneStyles} from "./PreviewScrollPaneStyles.tsx";

interface PreviewScrollPaneProps
{
    isEnlarged: boolean;
    exchangeDocument: DocumentDetailedDto;
    access: PreviewDocumentAccess;
    preview: PreviewDocument;
    layout: PreviewPageLayout;
    downloads: PreviewDownloads;
    canUploadDocument: boolean;
    onUploadDocument?: () => void;
}

const PreviewScrollPane = (
    {
        isEnlarged,
        exchangeDocument,
        access,
        preview,
        layout,
        downloads,
        canUploadDocument,
        onUploadDocument,
    }: PreviewScrollPaneProps) =>
{
    const styles = usePreviewScrollPaneStyles();
    const {pdfUrl, previewError, navigation} = preview;

    const renderBody = () =>
    {
        if (previewError)
        {
            return (
                <PreviewUnavailableState previewError={previewError}
                                         canDownloadOriginal={access.canDownloadOriginal}
                                         canDownloadAsPdf={access.canDownloadAsPdf}
                                         showDownloadDisabledNotice={access.showDownloadDisabledNotice}
                                         downloads={downloads}/>
            );
        }

        if (!pdfUrl && !exchangeDocument?.uploadDate)
        {
            return (
                <PreviewEmptyState documentTitle={exchangeDocument.title}
                                   isArchivedExchange={access.isArchivedExchange}
                                   canUploadDocument={canUploadDocument}
                                   onUploadDocument={onUploadDocument}/>
            );
        }

        if (!pdfUrl)
        {
            return (
                <PreviewLoadingIndicator containerId={"exchange-document-previewer-div-3"}
                                         spinnerId={"exchange-document-previewer-spinner-1"}
                                         textId={"exchange-document-previewer-text-9"}/>
            );
        }

        return (
            <PdfPageStack pdfUrl={pdfUrl}
                          navigation={navigation}
                          layout={layout}
                          watermarkText={access.watermarkText}
                          onRenderFailure={preview.reportRenderFailure}/>
        );
    };

    return (
        <div id="exchange-document-preview-main-panel"
             ref={navigation.pdfContainerRef}
             onScroll={navigation.handlePdfScroll}
             className={isEnlarged ? styles.mainDocumentPane : styles.pdfDocumentContainer}>
            {renderBody()}
        </div>
    );
};

export default PreviewScrollPane;
