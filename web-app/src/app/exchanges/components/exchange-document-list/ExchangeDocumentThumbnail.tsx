import React from "react";
import {Skeleton, SkeletonItem} from "@fluentui/react-components";
import {DocumentRegular} from "@fluentui/react-icons";
import {Document, Page, pdfjs} from "react-pdf";
import {downloadPreviewPDFExchangeDocument} from "../../../../services/exchangeApi.ts";
import {DocumentDetailedDto} from "../../../models/models.tsx";
import {useExchangeDocumentsListStyles} from "./ExchangeDocumentsListStyles.tsx";

pdfjs.GlobalWorkerOptions.workerSrc =
    `https://cdnjs.cloudflare.com/ajax/libs/pdf.js/${pdfjs.version}/pdf.worker.min.mjs`;

interface ExchangeDocumentThumbnailProps
{
    document: DocumentDetailedDto;
    exchangeId?: string;
}

const ExchangeDocumentThumbnail: React.FC<ExchangeDocumentThumbnailProps> = (props) =>
{
    const styles = useExchangeDocumentsListStyles();
    const [pdfUrl, setPdfUrl] = React.useState<string | null>(null);
    const [previewUnavailable, setPreviewUnavailable] = React.useState(false);
    const handlePreviewError = () =>
    {
        setPdfUrl(null);
        setPreviewUnavailable(true);
    };

    React.useEffect(() =>
    {
        if (!props.exchangeId || !props.document.id || !props.document.uploadDate)
        {
            setPdfUrl(null);
            setPreviewUnavailable(true);
            return;
        }

        let disposed = false;
        let objectUrl: string | null = null;
        setPdfUrl(null);
        setPreviewUnavailable(false);

        downloadPreviewPDFExchangeDocument(props.exchangeId, props.document.id)
            .then(response =>
            {
                if (disposed) return;
                objectUrl = URL.createObjectURL(new Blob([response as Blob], {type: "application/pdf"}));
                setPdfUrl(objectUrl);
            })
            .catch(() =>
            {
                if (!disposed) setPreviewUnavailable(true);
            });

        return () =>
        {
            disposed = true;
            if (objectUrl) URL.revokeObjectURL(objectUrl);
        };
    }, [props.document.id, props.document.uploadDate, props.exchangeId]);

    return (
        <div id={`exchange-document-thumbnail-${props.document.id}`}
             className={styles.thumbnailFrame}
             aria-hidden="true">
            {!pdfUrl && !previewUnavailable && (
                <Skeleton id={`exchange-document-thumbnail-loading-${props.document.id}`}>
                    <SkeletonItem id={`exchange-document-thumbnail-loading-item-${props.document.id}`}
                                  className={styles.thumbnailSkeleton}/>
                </Skeleton>
            )}
            {previewUnavailable && (
                <DocumentRegular className={styles.thumbnailPlaceholderIcon}/>
            )}
            {pdfUrl && (
                <Document file={pdfUrl}
                          loading={null}
                          onLoadError={handlePreviewError}>
                    <Page pageNumber={1}
                          width={200}
                          renderAnnotationLayer={false}
                          renderTextLayer={false}
                          onRenderError={handlePreviewError}
                          className={styles.thumbnailPage}/>
                </Document>
            )}
        </div>
    );
};

export default ExchangeDocumentThumbnail;
