import {useState} from 'react';
import {downloadExchangeDocument, downloadPreviewPDFExchangeDocument} from "../../../../services/exchangeApi";
import {DocumentDetailedDto} from "../../../models/models";

const saveBlob = (blob: Blob, fileName: string) =>
{
    const url = URL.createObjectURL(blob);
    const link = window.document.createElement('a');
    link.href = url;
    link.download = fileName;
    window.document.body.appendChild(link);
    link.click();
    window.document.body.removeChild(link);
    URL.revokeObjectURL(url);
};

export const usePreviewDownloads = (exchangeId: string, exchangeDocument: DocumentDetailedDto) =>
{
    const [downloadingOriginal, setDownloadingOriginal] = useState<boolean>(false);
    const [downloadingPdf, setDownloadingPdf] = useState<boolean>(false);

    const downloadOriginal = async () =>
    {
        if (!exchangeDocument?.id) return;
        setDownloadingOriginal(true);
        try
        {
            const blob = await downloadExchangeDocument(exchangeId, exchangeDocument.id) as Blob;
            saveBlob(blob, exchangeDocument.title || 'document');
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

    const downloadAsPdf = async () =>
    {
        if (!exchangeDocument?.id) return;
        setDownloadingPdf(true);
        try
        {
            const blob = await downloadPreviewPDFExchangeDocument(exchangeId, exchangeDocument.id);
            saveBlob(new Blob([blob as Blob], {type: 'application/pdf'}), `${exchangeDocument?.title ?? 'document'}.pdf`);
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

    return {downloadingOriginal, downloadingPdf, downloadOriginal, downloadAsPdf};
};

export type PreviewDownloads = ReturnType<typeof usePreviewDownloads>;
