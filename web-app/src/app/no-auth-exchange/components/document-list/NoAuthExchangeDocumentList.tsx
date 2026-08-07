import React, {useEffect, useState} from "react";
import {useNoAuthExchangeDocumentListStyles} from "./NoAuthExchangeDocumentListStyles";
import {DocumentBasicDto, DocumentDetailedDto, NoAuthExchangeBasicDto} from "../../../models/models";
import {Text} from "@fluentui/react-components";
import {
    downloadNoAuthExchangeDocument,
    uploadNoAuthExchangeDocument,
} from "../../../../services/exchangeApi";
import NoAuthExchangeDocumentCard from "./document-card/NoAuthExchangeDocumentCard";
import NoAuthExchangeAccessVerificationPanel from "./access-verification-panel/NoAuthExchangeAccessVerificationPanel";
import {
    getErrorMessage,
    isAccessVerificationError,
    requiresSignIn,
    toActionableUploadError,
} from "./noAuthExchangeErrors";

interface NoAuthExchangeDocumentListProps
{
    exchange: NoAuthExchangeBasicDto;
    onDocumentUploaded: (document: DocumentDetailedDto) => void;
}

const ACCESS_VERIFICATION_REASON =
    "Your access has expired. Ask the person who requested your documents to resend an access code, then enter it below to continue.";

const NoAuthExchangeDocumentList: React.FC<NoAuthExchangeDocumentListProps> = ({exchange, onDocumentUploaded}) =>
{
    const styles = useNoAuthExchangeDocumentListStyles();
    const [uploading, setUploading] = useState<Record<string, boolean>>({});
    const [progress, setProgress] = useState<Record<string, number>>({});
    const [selectedFiles, setSelectedFiles] = useState<Record<string, File | null>>({});
    const [downloadingDocument, setDownloadingDocument] = useState<boolean>(false);
    const [uploadErrors, setUploadErrors] = useState<Record<string, string>>({});
    const [isUploadBlockedBySignInRequirement, setIsUploadBlockedBySignInRequirement] = useState<boolean>(false);
    const [accessVerificationRequired, setAccessVerificationRequired] = useState<boolean>(false);
    const exchangeDocuments: DocumentBasicDto[] = exchange.documents || [];
    const accessWindowDays = Math.max(1, exchange.noAuthAccessValidityDays ?? 7);

    // The API reports a lapsed access window when the Exchange loads, so the prompt appears
    // immediately instead of only after the recipient's first upload attempt fails.
    useEffect(() =>
    {
        setAccessVerificationRequired(exchange.accessVerificationRequired === true);
    }, [exchange.accessVerificationRequired]);

    const onAccessVerified = () =>
    {
        setAccessVerificationRequired(false);
        setUploadErrors({});
    };

    /**
     * Routes a failure to a single owner: an access-window failure belongs to the verification
     * panel only, everything else belongs to the document card that produced it. Without this
     * split the same sentence was rendered in the panel and again on every document card.
     */
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
            const blob = new Blob([data as BlobPart], {type: "application/octet-stream"});
            const url = window.URL.createObjectURL(blob);
            const downloadLink = document.createElement("a");
            downloadLink.href = url;
            downloadLink.setAttribute("download", `${doc.title}.pdf`);
            document.body.appendChild(downloadLink);
            downloadLink.click();
            downloadLink.remove();
            window.URL.revokeObjectURL(url);
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

    return (
        <section className={styles.container}>
            {!accessVerificationRequired && (
                <Text size={200}
                      className={styles.accessWindowHint}>
                    This secure email link works for {accessWindowDays} day{accessWindowDays === 1 ? '' : 's'} after
                    verification. After that, ask the requester to resend a new access code and link.
                </Text>
            )}
            {accessVerificationRequired && (
                <NoAuthExchangeAccessVerificationPanel exchangeId={exchange.id}
                                                       reason={ACCESS_VERIFICATION_REASON}
                                                       disabled={isUploadBlockedBySignInRequirement}
                                                       onVerified={onAccessVerified}/>
            )}
            {exchangeDocuments.map((doc) => (
                <NoAuthExchangeDocumentCard key={doc.id}
                                            document={doc}
                                            exchangeId={exchange.id}
                                            selectedFileName={selectedFiles[doc.id]?.name ?? ""}
                                            uploading={!!uploading[doc.id]}
                                            uploadProgress={progress[doc.id] || 0}
                                            error={uploadErrors[doc.id] || ""}
                                            uploadsDisabled={
                                                !!uploading[doc.id] ||
                                                isUploadBlockedBySignInRequirement ||
                                                accessVerificationRequired
                                            }
                                            downloadEnabled={!!doc.uploadDate && !!exchange.allowDocumentDownload}
                                            downloading={downloadingDocument || accessVerificationRequired}
                                            onFileSelected={(event) => handleFileSelectChange(event, doc.id)}
                                            onUpload={() => handleDocumentUpload(doc.id)}
                                            onDownload={() => handleDownload(doc)}/>
            ))}
        </section>
    );
};

export default NoAuthExchangeDocumentList;
