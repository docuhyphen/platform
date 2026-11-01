import {useEffect, useState} from "react";
import {ExchangeDetailedDto} from "../../../models/models.tsx";
import {downloadExchangeDocumentZip} from "../../../../services/exchangeApi.ts";
import {saveZipFile} from "./exchangeDocumentZipFile.ts";

export interface ExchangeDocumentZipDownload
{
    downloadName: string;
    setDownloadName: (downloadName: string) => void;
    downloadingDocumentsZip: boolean;
    dialogErrorMessage: string | null;
    onDownload: () => Promise<void>;
    onClose: () => void;
    handleDocumentSelection: (documentId: string, isSelected: boolean) => void;
}

export const useExchangeDocumentZipDownload = (
    exchange: ExchangeDetailedDto,
    onDismiss: () => void,
): ExchangeDocumentZipDownload =>
{
    const [downloadName, setDownloadName] = useState('');
    const [downloadingDocumentsZip, setDownloadingDocumentsZip] = useState(false);
    const [selectedDocuments, setSelectedDocuments] = useState<string[]>([]);
    const [dialogErrorMessage, setDialogErrorMessage] = useState<string | null>(null);

    useEffect(() =>
    {
        setDownloadName(`${exchange?.name?.replace(/\s+/g, '-')}-documents`);
    }, [exchange]);

    const onDownload = async () =>
    {
        setDownloadingDocumentsZip(true);

        try
        {
            const data = await downloadExchangeDocumentZip(exchange.id, {documentIds: selectedDocuments});
            saveZipFile(data, downloadName);
        }
        catch (error)
        {
            setDialogErrorMessage("Download failed");
            console.error("Error downloading document:", error);
        }
        finally
        {
            setDownloadingDocumentsZip(false);
        }
    };

    const onClose = () =>
    {
        setDownloadName('');
        onDismiss();
    };

    const handleDocumentSelection = (documentId: string, isSelected: boolean) =>
    {
        setSelectedDocuments(prevSelectedDocuments =>
            isSelected
                ? [...prevSelectedDocuments, documentId]
                : prevSelectedDocuments.filter(id => id !== documentId)
        );
    };

    return {
        downloadName,
        setDownloadName,
        downloadingDocumentsZip,
        dialogErrorMessage,
        onDownload,
        onClose,
        handleDocumentSelection
    };
};
