import React from "react";
import {useNoAuthExchangeDocumentListStyles} from "./NoAuthExchangeDocumentListStyles";
import {DocumentBasicDto, DocumentDetailedDto, NoAuthExchangeBasicDto} from "../../../models/models";
import {Text} from "@fluentui/react-components";
import NoAuthExchangeDocumentCard from "./document-card/NoAuthExchangeDocumentCard";
import NoAuthExchangeAccessVerificationPanel from "./access-verification-panel/NoAuthExchangeAccessVerificationPanel";
import useNoAuthExchangeDocumentList from "./useNoAuthExchangeDocumentList";

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
    const {
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
    } = useNoAuthExchangeDocumentList(exchange, onDocumentUploaded);
    const exchangeDocuments: DocumentBasicDto[] = exchange.documents || [];
    const accessWindowDays = Math.max(1, exchange.noAuthAccessValidityDays ?? 7);

    return (
        <section id={"no-auth-exchange-document-list-section-1"}
                 className={styles.container}>
            {!accessVerificationRequired && (
                <Text id={"no-auth-exchange-document-list-text-1"}
                      size={200}
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
