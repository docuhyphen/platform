import React, {useEffect, useState} from "react";
import {DocumentBasicDto, DocumentDetailedDto, NoAuthExchangeBasicDto} from "../../../models/models";
import {downloadNoAuthExchangeDocument, uploadNoAuthExchangeDocument} from "../../../../services/exchangeApi";
import {
    getErrorMessage,
    isAccessVerificationError,
    requiresSignIn,
    toActionableUploadError,
} from "./noAuthExchangeErrors";

const saveBlobAsFile = (data: unknown, fileName: string) =>
{
    const blob = new Blob([data as BlobPart], {type: "application/octet-stream"});
    const url = window.URL.createObjectURL(blob);
    const downloadLink = document.createElement("a");
    downloadLink.href = url;
    downloadLink.setAttribute("download", fileName);
    document.body.appendChild(downloadLink);
    downloadLink.click();
    downloadLink.remove();
    window.URL.revokeObjectURL(url);
};

const useNoAuthExchangeDocumentList = (
    exchange: NoAuthExchangeBasicDto,
    onDocumentUploaded: (document: DocumentDetailedDto) => void,
) =>
{
    const [uploading, setUploading] = useState<Record<string, boolean>>({});
    const [progress, setProgress] = useState<Record<string, number>>({});
    const [selectedFiles, setSelectedFiles] = useState<Record<string, File | null>>({});
    const [downloadingDocument, setDownloadingDocument] = useState<boolean>(false);
    const [uploadErrors, setUploadErrors] = useState<Record<string, string>>({});
    const [isUploadBlockedBySignInRequirement, setIsUploadBlockedBySignInRequirement] = useState<boolean>(false);
    const [accessVerificationRequired, setAccessVerificationRequired] = useState<boolean>(false);

    useEffect(() =>
    {
        setAccessVerificationRequired(exchange.accessVerificationRequired === true);
    }, [exchange.accessVerificationRequired]);

    const onAccessVerified = () =>
    {
        setAccessVerificationRequired(false);
        setUploadErrors({});
    };

    const handleActionError = (error: unknown, documentId: string, fallback: string) =>
    {
        const message = getErrorMessage(error, fallback);

        if (requiresSignIn(message))
        {
            setIsUploadBlockedBySignInRequirement(true);
        }

        if (isAccessVerificationError(error))
        {
            setAccessVerificationRequired(true);
            setUploadErrors((prev) => ({...prev, [documentId]: ""}));
            return;
        }

        setUploadErrors((prev) => ({...prev, [documentId]: toActionableUploadError(message)}));
    };

    const handleFileSelectChange = (e: React.ChangeEvent<HTMLInputElement>, docId: string) =>
    {
        const file = e.target.files ? e.target.files[0] : null;
        if (file)
        {
            setUploadErrors((prev) => ({...prev, [docId]: ''}));
            setSelectedFiles((prev) => ({...prev, [docId]: file}));
        }
    };

    const handleUploadDocument = async (docId: string, file: File) =>
    {
        setUploadErrors((prev) => ({...prev, [docId]: ''}));
        setUploading((prev) => ({...prev, [docId]: true}));
        const fileName = file.name;
        const fileExtension = fileName.substring(fileName.lastIndexOf(".")) || "";

        const formData = new FormData();
        formData.append("file", file, fileName);
        formData.append("encryptionMode", "INTERNAL");
        formData.append("extension", fileExtension);

        try
        {
            const uploadedDocument = await uploadNoAuthExchangeDocument(exchange.id, docId, formData, (event) =>
            {
                const total = event.total ?? 0;
                const percentCompleted = total > 0 ? Math.round((event.loaded * 100) / total) : 0;
                setProgress((prev) => ({...prev, [docId]: percentCompleted}));
            });
            onDocumentUploaded(uploadedDocument);
            setSelectedFiles((prev) => ({...prev, [docId]: null}));
        }
        catch (error)
        {
            handleActionError(error, docId, "Could not upload the document. Please try again.");
            console.error(error);
        }
        finally
        {
            setUploading((prev) => ({...prev, [docId]: false}));
        }
    };

    const handleDocumentUpload = (docId: string) =>
    {
        const file = selectedFiles[docId];
        if (file)
        {
            handleUploadDocument(docId, file);
        }
        else
        {
            setUploadErrors((prev) => ({...prev, [docId]: "Select a file first, then click Upload new document."}));
        }
    };

    const handleDownload = async (doc: DocumentBasicDto) =>
    {
        setDownloadingDocument(true);
        try
        {
            const data = await downloadNoAuthExchangeDocument(exchange.id, doc.id);
            saveBlobAsFile(data, `${doc.title}.pdf`);
        }
        catch (error)
        {
            handleActionError(error, doc.id, "Could not download the document. Please try again.");
            console.error("Error downloading document:", error);
        }
        finally
        {
            setDownloadingDocument(false);
        }
    };

    return {
        uploading,
        progress,
        selectedFiles,
        downloadingDocument,
        uploadErrors,
        isUploadBlockedBySignInRequirement,
        accessVerificationRequired,
        onAccessVerified,
        handleFileSelectChange,
        handleDocumentUpload,
        handleDownload,
    };
};

export default useNoAuthExchangeDocumentList;
