import React from "react";
import {Button, Card, CardHeader, MessageBar, MessageBarBody, ProgressBar, Spinner, Text, mergeClasses} from "@fluentui/react-components";
import {DocumentBasicDto} from "../../../../models/models";
import {DocumentAddIcon, DownloadIcon, UploadIcon} from "../../../../components/IconBundles";
import {formatDateTimeWithOrdinal} from "../../../../helpers";
import NoAuthExchangeDocumentThumbnail from "../document-thumbnail/NoAuthExchangeDocumentThumbnail";
import {useNoAuthExchangeDocumentCardStyles} from "./NoAuthExchangeDocumentCardStyles";

interface NoAuthExchangeDocumentCardProps
{
    document: DocumentBasicDto;
    exchangeId: string;
    selectedFileName: string;
    uploading: boolean;
    uploadProgress: number;
    /** Card-scoped failure only. Access-window failures are owned by the verification panel. */
    error: string;
    uploadsDisabled: boolean;
    downloadEnabled: boolean;
    downloading: boolean;
    onFileSelected: (event: React.ChangeEvent<HTMLInputElement>) => void;
    onUpload: () => void;
    onDownload: () => void;
}

const NoAuthExchangeDocumentCard: React.FC<NoAuthExchangeDocumentCardProps> = (props) =>
{
    const styles = useNoAuthExchangeDocumentCardStyles();
    const documentId = props.document.id;

    return (
        <Card id={`no-auth-exchange-document-card-${documentId}`}
              className={styles.documentCard}>
            {!!props.error && (
                <MessageBar intent="error"
                            className={styles.documentError}>
                    <MessageBarBody className={styles.documentErrorBody}>{props.error}</MessageBarBody>
                </MessageBar>
            )}
            {props.document.uploadDate && (
                <NoAuthExchangeDocumentThumbnail document={props.document}
                                                 exchangeId={props.exchangeId}/>
            )}
            <CardHeader className={styles.documentCardHeader}
                        header={
                            <div className={styles.documentName}>
                                <Text size={300}
                                      weight={"semibold"}
                                      className={styles.documentTitle}>
                                    {props.document.title}
                                </Text>
                                {props.document.uploadDate && (
                                    <Text className={styles.uploadedDate}>
                                        File received {formatDateTimeWithOrdinal(props.document.uploadDate)}
                                    </Text>
                                )}
                            </div>
                        }/>
            <div className={styles.documentActions}>
                <div className={styles.documentActionsLine1}>
                    <div className={styles.uploadActions}>
                        <Button id={`no-auth-exchange-doc-choose-file-btn-${documentId}`}
                                appearance="secondary"
                                size={"small"}
                                icon={<DocumentAddIcon/>}
                                shape="circular"
                                disabled={props.uploadsDisabled}
                                className={mergeClasses(styles.uploadButton1, styles.actionButton)}>
                            {props.selectedFileName ? "Choose another file" : "Choose file"}
                            <input type="file"
                                   onChange={props.onFileSelected}
                                   className={styles.uploadButton2}
                                   disabled={props.uploadsDisabled}/>
                        </Button>
                        <Button id={`no-auth-exchange-doc-upload-btn-${documentId}`}
                                appearance="primary"
                                size={"small"}
                                icon={<UploadIcon/>}
                                shape="circular"
                                onClick={props.onUpload}
                                disabled={props.uploadsDisabled}
                                className={styles.actionButton}>
                            {props.uploading ? <Spinner size="tiny"/> : "Upload file"}
                        </Button>
                    </div>
                    {props.downloadEnabled && (
                        <Button id={`no-auth-exchange-doc-download-btn-${documentId}`}
                                appearance="secondary"
                                disabled={props.downloading}
                                shape="circular"
                                size={"small"}
                                icon={<DownloadIcon/>}
                                onClick={props.onDownload}
                                className={mergeClasses(styles.actionButton, styles.downloadAction)}>
                            Download
                        </Button>
                    )}
                </div>
                <div className={styles.documentActionsLine2}>
                    {props.selectedFileName && (
                        <Text size={300}
                              weight="semibold"
                              className={styles.fileNameText}>
                            Chosen file: {props.selectedFileName}
                        </Text>
                    )}
                </div>
            </div>
            {props.uploading && <ProgressBar value={props.uploadProgress / 100}/>}
        </Card>
    );
};

export default NoAuthExchangeDocumentCard;

