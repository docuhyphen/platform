import React, {useState} from "react";
import {useNoAuthExchangeDocumentListStyles} from "./NoAuthExchangeDocumentListStyles";
import {DocumentBasicDto, DocumentDetailedDto, NoAuthExchangeBasicDto} from "../../../models/models";
import {Button, Card, CardHeader, Field, Input, MessageBar, MessageBarBody, ProgressBar, Spinner, Text} from "@fluentui/react-components";
import {mergeClasses} from "@fluentui/react-components";
import {DocumentAddIcon, DownloadIcon, UploadIcon} from "../../../components/IconBundles";
import {formatDateTimeWithOrdinal} from "../../../helpers";
import {
    downloadNoAuthExchangeDocument,
    uploadNoAuthExchangeDocument,
    verifyNoAuthExchangeAccessCode,
} from "../../../../services/exchangeApi";

interface NoAuthExchangeDocumentListProps
{
    exchange: NoAuthExchangeBasicDto;
    onDocumentUploaded: (document: DocumentDetailedDto) => void;
}

const NoAuthExchangeDocumentList: React.FC<NoAuthExchangeDocumentListProps> = ({exchange, onDocumentUploaded}) =>
{
    const styles = useNoAuthExchangeDocumentListStyles();
    const [uploading, setUploading] = useState<Record<string, boolean>>({});
    const [progress, setProgress] = useState<Record<string, number>>({});
    const [selectedFiles, setSelectedFiles] = useState<Record<string, File | null>>({});
    const [downloadingDocument, setDownloadingDocument] = useState<boolean>(false);
    const [uploadErrors, setUploadErrors] = useState<Record<string, string>>({});
    const [isUploadBlockedBySignInRequirement, setIsUploadBlockedBySignInRequirement] = useState<boolean>(false);
    const [accessCode, setAccessCode] = useState<string>('');
    const [verifyingAccessCode, setVerifyingAccessCode] = useState<boolean>(false);
    const [accessVerificationRequired, setAccessVerificationRequired] = useState<boolean>(false);
    const [accessVerificationNotice, setAccessVerificationNotice] = useState<string>('');
    const exchangeDocuments: DocumentBasicDto[] = exchange.documents || [];
    const accessWindowDays = Math.max(1, exchange.noAuthAccessValidityDays ?? 7);

    const getErrorMessage = (error: unknown, fallback: string): string =>
    {
        if (typeof error === "string")
        {
            return error;
        }
        if (typeof error === "object" && error !== null)
        {
            const apiError = error as { errorMessage?: string; message?: string };
            return apiError.errorMessage || apiError.message || fallback;
        }
        return fallback;
    };

    const toActionableUploadError = (message: string): string =>
    {
        if (/Permission to upload document not granted/i.test(message))
        {
            return "You cannot upload to this document because uploads are disabled for this request. Ask the person who requested your documents to enable uploads in Manage Access, or send the file to them outside the app.";
        }
        if (/Sharing exchange has already ended/i.test(message) || /exchange has ended/i.test(message))
        {
            return "This request has ended, so uploads are no longer possible. Contact the person who requested your documents outside the app if you still need to send this file.";
        }
        if (/requires sign\s?in/i.test(message) || /requires recipient sign-?in/i.test(message))
        {
            return "This request now requires sign in. Sign in to your account and reopen the request to continue uploading documents.";
        }
        if (/access verification has expired/i.test(message))
        {
            return "Your no-auth access has expired. Ask the person who requested your documents to resend an access code, then enter it to continue.";
        }
        if (/verification code/i.test(message))
        {
            return "That access code is invalid or expired. Ask the requester to resend a new code and try again.";
        }
        return message;
    };

    const isAccessVerificationError = (message: string): boolean =>
    {
        return /access verification has expired/i.test(message);
    };

    const onVerifyAccessCode = async () =>
    {
        if (verifyingAccessCode)
        {
            return;
        }

        const trimmedCode = accessCode.trim();
        if (trimmedCode.length < 4)
        {
            setUploadErrors((prev) => ({
                ...prev,
                ['__global__']: 'Enter the access code that was resent by the requester, then verify again.',
            }));
            return;
        }

        setVerifyingAccessCode(true);
        setAccessVerificationNotice('');
        try
        {
            await verifyNoAuthExchangeAccessCode(exchange.id, trimmedCode);
            setAccessVerificationRequired(false);
            setUploadErrors((prev) => ({...prev, ['__global__']: ''}));
            setAccessVerificationNotice('Access verified. You can continue uploading documents.');
            setAccessCode('');
        }
        catch (error)
        {
            const message = getErrorMessage(error, 'Could not verify access code. Please try again.');
            setUploadErrors((prev) => ({...prev, ['__global__']: toActionableUploadError(message)}));
        }
        finally
        {
            setVerifyingAccessCode(false);
        }
    };

    const onAccessCodeChange = (value: string) =>
    {
        setAccessCode(value.replace(/\D/g, '').slice(0, 8));
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
            const uploadedDocument = await uploadNoAuthExchangeDocument(exchange.id, docId, formData, (event: ProgressEvent) =>
            {
                const percentCompleted = Math.round((event.loaded * 100) / event.total);
                setProgress((prev) => ({...prev, [docId]: percentCompleted}));
            });
            onDocumentUploaded(uploadedDocument);
            setSelectedFiles((prev) => ({...prev, [docId]: null}));
        }
        catch (error)
        {
            const message = getErrorMessage(error, "Could not upload the document. Please try again.");
            if (/requires sign\s?in/i.test(message) || /requires recipient sign-?in/i.test(message))
            {
                setIsUploadBlockedBySignInRequirement(true);
            }
            if (isAccessVerificationError(message))
            {
                setAccessVerificationRequired(true);
                setUploadErrors((prev) => ({...prev, ['__global__']: toActionableUploadError(message)}));
            }
            setUploadErrors((prev) => ({...prev, [docId]: toActionableUploadError(message)}));
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
            downloadLink.setAttribute("download", `${doc.title}.pdf`); // TODO: determine correct file type if needed
            document.body.appendChild(downloadLink);
            downloadLink.click();
            downloadLink.remove();
        }
        catch (error)
        {
            const message = getErrorMessage(error, "Download failed");
            if (isAccessVerificationError(message))
            {
                setAccessVerificationRequired(true);
                setUploadErrors((prev) => ({...prev, ['__global__']: toActionableUploadError(message)}));
            }
            console.error("Error downloading document:", error);
        }
        finally
        {
            setDownloadingDocument(false);
        }
    };

    const renderDocumentCard = (doc: DocumentBasicDto) =>
    {
        const selectedFile = selectedFiles[doc.id];
        const fileName = selectedFile ? selectedFile.name : "";
        return (
            <Card
                id={`no-auth-exchange-document-card-${doc.id}`}
                key={doc.id}
                className={styles.documentCard}
            >
                {!!uploadErrors[doc.id] && (
                    <MessageBar intent="error" className={styles.documentError}>
                        <MessageBarBody className={styles.documentErrorBody}>{uploadErrors[doc.id]}</MessageBarBody>
                    </MessageBar>
                )}
                <CardHeader
                    className={styles.documentCardHeader}
                    header={
                        <div className={styles.documentName}>
                            <Text
                                size={500}
                                weight={"semibold"}
                                className={styles.documentTitle}
                            >
                                {doc.title}
                            </Text>
                            {doc.uploadDate && (
                                <Text className={styles.uploadedDate}>
                                    File received {formatDateTimeWithOrdinal(doc.uploadDate)}
                                </Text>
                            )}
                        </div>
                    }
                />
                <div className={styles.documentActions}>
                    <div className={styles.documentActionsLine1}>
                        <div className={styles.uploadActions}>
                            <Button
                                id={`no-auth-exchange-doc-choose-file-btn-${doc.id}`}
                                appearance="secondary"
                                icon={<DocumentAddIcon/>}
                                shape="circular"
                                disabled={uploading[doc.id] || isUploadBlockedBySignInRequirement}
                                className={mergeClasses(styles.uploadButton1, styles.actionButton)}
                            >
                                {fileName ? "Choose another file" : "Choose file"}
                                <input
                                    type="file"
                                    onChange={(e) => handleFileSelectChange(e, doc.id)}
                                    className={styles.uploadButton2}
                                    disabled={uploading[doc.id] || isUploadBlockedBySignInRequirement}
                                />
                            </Button>
                            <Button
                                id={`no-auth-exchange-doc-upload-btn-${doc.id}`}
                                appearance="primary"
                                icon={<UploadIcon/>}
                                shape="circular"
                                onClick={() => handleDocumentUpload(doc.id)}
                                disabled={uploading[doc.id] || isUploadBlockedBySignInRequirement || accessVerificationRequired}
                                className={styles.actionButton}
                            >
                                {uploading[doc.id] ? <Spinner size="tiny"/> : "Upload file"}
                            </Button>
                        </div>
                        {doc.uploadDate && (
                            <Button
                                id={`no-auth-exchange-doc-download-btn-${doc.id}`}
                                appearance="secondary"
                                disabled={downloadingDocument}
                                shape="circular"
                                icon={<DownloadIcon/>}
                                onClick={() => handleDownload(doc)}
                                className={mergeClasses(styles.actionButton, styles.downloadAction)}
                            >
                                Download
                            </Button>
                        )}
                    </div>
                    <div className={styles.documentActionsLine2}>
                        {fileName && (
                            <Text size={300} weight="semibold" className={styles.fileNameText}>
                                Chosen file: {fileName}
                            </Text>
                        )}
                    </div>
                </div>
                {uploading[doc.id] && <ProgressBar value={(progress[doc.id] || 0) / 100}/>}
            </Card>
        );
    };

    return (
        <section className={styles.container}>
            <Text size={200} className={styles.accessWindowHint}>
                Your verified access works across browsers and devices for {accessWindowDays} day{accessWindowDays === 1 ? '' : 's'}.
                After that, ask the requester to resend a new access code.
            </Text>
            {accessVerificationRequired && (
                <section className={styles.verificationPanel}>
                    <Text weight="semibold">Access verification required</Text>
                    <Text size={200}>Ask the requester to resend an access code in Manage Access, then enter that code here.</Text>
                    <div className={styles.verificationControls}>
                        <Field label="Access code" className={styles.otpInputField}>
                            <Input
                                id={"no-auth-exchange-access-code-input"}
                                value={accessCode}
                                onChange={(_, data) => onAccessCodeChange(data.value)}
                                placeholder="Enter access code"
                                inputMode="numeric"
                            />
                        </Field>
                        <Button
                            id={"no-auth-exchange-verify-code-btn"}
                            appearance="secondary"
                            shape="circular"
                            onClick={onVerifyAccessCode}
                            disabled={verifyingAccessCode || isUploadBlockedBySignInRequirement}
                        >
                            {verifyingAccessCode ? <Spinner size="tiny"/> : "Verify code"}
                        </Button>
                    </div>
                    {accessVerificationNotice && (
                        <MessageBar intent="success">
                            <MessageBarBody>{accessVerificationNotice}</MessageBarBody>
                        </MessageBar>
                    )}
                    {!!uploadErrors['__global__'] && (
                        <MessageBar intent="error" className={styles.documentError}>
                            <MessageBarBody className={styles.documentErrorBody}>{uploadErrors['__global__']}</MessageBarBody>
                        </MessageBar>
                    )}
                </section>
            )}
            {exchangeDocuments.map(renderDocumentCard)}
        </section>
    );
};

export default NoAuthExchangeDocumentList;
