import {Page} from 'react-pdf';
import {PagePresentationProps} from "../previewPageVirtualization.ts";
import {usePdfPageItemStyles} from "./PdfPageItemStyles.tsx";

interface PdfPageItemProps
{
    pageNumber: number;
    isVisible: boolean;
    pagePresentationProps: PagePresentationProps;
    onPageLoadSuccess: (pdfPage: { width: number; height: number }) => void;
    registerPageRef: (pageNumber: number, element: HTMLDivElement | null) => void;
}

const PdfPageItem = (
    {
        pageNumber,
        isVisible,
        pagePresentationProps,
        onPageLoadSuccess,
        registerPageRef,
    }: PdfPageItemProps) =>
{
    const styles = usePdfPageItemStyles();

    return (
        <div id={`exchange-document-previewer-div-7-${`page-${pageNumber}`}`}
             data-page-number={pageNumber}
             ref={(el) => registerPageRef(pageNumber, el)}
             className={styles.pdfPageItem}>
            {isVisible ? (
                <Page className={styles.pdfPageAnimated}
                      pageNumber={pageNumber}
                      {...pagePresentationProps}
                      onLoadSuccess={onPageLoadSuccess}
                      loading={
                          <div id={"exchange-document-previewer-div-8"}
                               className={styles.pdfPagePlaceholder}>
                              Loading page {pageNumber}…
                          </div>
                      }
                />
            ) : (
                <Page pageNumber={pageNumber}
                      {...pagePresentationProps}
                      renderMode="none"
                      renderTextLayer={false}
                      renderAnnotationLayer={false}
                      onLoadSuccess={onPageLoadSuccess}
                      loading={
                          <div id={"exchange-document-previewer-div-9"}
                               className={styles.pdfPagePlaceholder}>
                              Page {pageNumber}
                          </div>
                      }
                />
            )}
        </div>
    );
};

export default PdfPageItem;
