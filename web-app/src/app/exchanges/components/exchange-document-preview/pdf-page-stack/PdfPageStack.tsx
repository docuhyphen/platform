import {Document} from 'react-pdf';
import {PreviewPageNavigation} from "../usePreviewPageNavigation.ts";
import {PreviewPageLayout} from "../usePreviewPageLayout.ts";
import PreviewLoadingIndicator from "../preview-loading-indicator/PreviewLoadingIndicator.tsx";
import PdfPageItem from "../pdf-page-item/PdfPageItem.tsx";
import PreviewWatermark from "../preview-watermark/PreviewWatermark.tsx";
import {usePdfPageStackStyles} from "./PdfPageStackStyles.tsx";

interface PdfPageStackProps
{
    pdfUrl: string;
    navigation: PreviewPageNavigation;
    layout: PreviewPageLayout;
    watermarkText: string | undefined;
    onRenderFailure: (error: unknown) => void;
}

const PdfPageStack = ({pdfUrl, navigation, layout, watermarkText, onRenderFailure}: PdfPageStackProps) =>
{
    const styles = usePdfPageStackStyles();

    return (
        <div id={"exchange-document-previewer-div-4"}
             className={styles.pdfRelativeWrapper}>
            <Document className={styles.pdfDocument}
                      file={pdfUrl}
                      loading={
                          <PreviewLoadingIndicator containerId={"exchange-document-previewer-div-5"}
                                                   spinnerId={"exchange-document-previewer-spinner-2"}
                                                   textId={"exchange-document-previewer-text-10"}/>
                      }
                      onLoadSuccess={navigation.onDocumentLoadSuccess}
                      onLoadError={onRenderFailure}>
                {navigation.numPages > 0 && (
                    <div id={"exchange-document-previewer-div-6"}
                         className={styles.pdfPagesStack}>
                        {Array.from({length: navigation.numPages}, (_, index) =>
                        {
                            const pageNumber = index + 1;
                            return (
                                <PdfPageItem key={`page-${pageNumber}`}
                                             pageNumber={pageNumber}
                                             isVisible={navigation.visiblePages.has(pageNumber)}
                                             pagePresentationProps={layout.pagePresentationProps}
                                             onPageLoadSuccess={layout.handlePageLoadSuccess}
                                             registerPageRef={navigation.registerPageRef}/>
                            );
                        })}
                    </div>
                )}
            </Document>
            {watermarkText !== undefined && (
                <PreviewWatermark watermarkText={watermarkText}/>
            )}
        </div>
    );
};

export default PdfPageStack;
