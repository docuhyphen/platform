import {useEffect, useState} from 'react';
import {downloadPreviewPDFExchangeDocument} from "../../../../services/exchangeApi";
import {DocumentDetailedDto} from "../../../models/models";
import {usePreviewPageNavigation} from './usePreviewPageNavigation.ts';

const describePreviewError = (error: unknown, downloadAllowed: boolean): string =>
{
    const dlMsg = downloadAllowed ? "You can still download the original file" : '';
    return (typeof error === 'object' && error && 'message' in error)
        ? String((error as { message?: unknown }).message)
        : `Preview is unavailable for this document. ${dlMsg}`;
};

export const usePreviewDocument = (
    exchangeId: string,
    exchangeDocument: DocumentDetailedDto,
    overridePdfUrl: string | undefined,
    downloadAllowed: boolean,
) =>
{
    const [pdfUrl, setPdfUrl] = useState<string | null>(null);
    const [previewError, setPreviewError] = useState<string | null>(null);
    const navigation = usePreviewPageNavigation(pdfUrl);
    const {resetToFirstPage} = navigation;

    useEffect(() =>
    {
        if (overridePdfUrl)
        {
            setPdfUrl(overridePdfUrl);
            setPreviewError(null);
            resetToFirstPage();
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
                    const response = await downloadPreviewPDFExchangeDocument(exchangeId, exchangeDocument.id);
                    const blob = new Blob([response as Blob], {type: 'application/pdf'});
                    const url = URL.createObjectURL(blob);
                    revokedUrl = url;
                    setPdfUrl(url);
                }
                catch (error: unknown)
                {
                    console.error("Error downloading document:", error);
                    setPreviewError(describePreviewError(error, downloadAllowed));
                }
            }
            else
            {
                setPreviewError(null);
                setPdfUrl(null);
            }
        };
        fetchDocument();
        resetToFirstPage();
        return () =>
        {
            if (revokedUrl)
            {
                URL.revokeObjectURL(revokedUrl);
            }
        };
    }, [downloadAllowed, exchangeDocument, exchangeId, overridePdfUrl, resetToFirstPage]);

    const reportRenderFailure = (error: unknown) =>
    {
        console.error("Failed to load PDF:", error);
        const dlMsg = downloadAllowed ? "You can still download the original file" : '';
        setPreviewError(`The preview could not be rendered. ${dlMsg}`);
    };

    return {pdfUrl, previewError, reportRenderFailure, navigation};
};

export type PreviewDocument = ReturnType<typeof usePreviewDocument>;
