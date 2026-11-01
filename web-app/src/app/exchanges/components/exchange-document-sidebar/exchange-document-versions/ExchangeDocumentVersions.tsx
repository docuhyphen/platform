import React, {useState} from "react";
import {Button, Spinner, Text} from "@fluentui/react-components";
import {ArrowUploadRegular} from "@fluentui/react-icons";
import {DocumentDetailedDto, ExchangeDetailedDto} from "../../../../models/models";
import {useExchangeDocumentVersionsStyles} from "./ExchangeDocumentVersionsStyles.tsx";
import ExchangeDocumentUploadDialog
    from "../../exchange-document-upload-dialog/ExchangeDocumentUploadDialog.tsx";
import ExchangeDocumentVersionCard from "./exchange-document-version-card/ExchangeDocumentVersionCard.tsx";
import ExchangeDocumentVersionPreviewDialog
    from "./exchange-document-version-preview-dialog/ExchangeDocumentVersionPreviewDialog.tsx";
import {useExchangeDocumentVersions} from "./useExchangeDocumentVersions.ts";

interface ExchangeDocumentVersionsProps
{
    exchangeId: string;
    exchangeDocument: DocumentDetailedDto;
    exchange: ExchangeDetailedDto;
    canUpload: boolean;
    canDownload: boolean;
}

const ExchangeDocumentVersions: React.FC<ExchangeDocumentVersionsProps> = (
    {
        exchangeId,
        exchangeDocument,
        exchange,
        canUpload,
        canDownload,
    }) =>
{
    const styles = useExchangeDocumentVersionsStyles();
    const [isUploadDialogOpen, setIsUploadDialogOpen] = useState(false);
    const {
        versions,
        loading,
        error,
        actionInProgress,
        previewUrl,
        previewVersionId,
        fetchVersions,
        closePreview,
        previewVersion,
        downloadVersion,
    } = useExchangeDocumentVersions(exchangeId, exchangeDocument);

    if (loading)
    {
        return <Spinner id={"exchange-document-versions-spinner-1"}
                        size="small"/>;
    }

    return (
        <div id={"exchange-document-versions-div-1"}
             className={styles.container}>
            {error && (
                <Text id={"exchange-document-versions-text-1"}
                      className={styles.errorText}>{error}</Text>
            )}

            <div id={"exchange-document-versions-div-2"}
                 className={styles.versionList}>
                {versions.length === 0 ? (
                    <div id={"exchange-document-versions-div-3"}
                         className={styles.noVersions}>
                        <Text id={"exchange-document-versions-text-2"}
                              align="center">No versions available</Text>
                        <Text id={"exchange-document-versions-text-3"}
                              size={200}
                              align="center">
                            Upload a version to see it here
                        </Text>
                    </div>
                ) : (
                    versions.map((version) => (
                        <ExchangeDocumentVersionCard key={version.id}
                                                     version={version}
                                                     canDownload={canDownload}
                                                     actionInProgress={actionInProgress === version.id}
                                                     onPreview={previewVersion}
                                                     onDownload={downloadVersion}/>
                    ))
                )}
            </div>

            {canUpload && (
                <div id={"exchange-document-versions-div-7"}
                     className={styles.uploadButtonRow}>
                    <Button id={"exchange-document-version-upload-btn"}
                            appearance="secondary"
                            shape="circular"
                            size="small"
                            icon={<ArrowUploadRegular/>}
                            onClick={() => setIsUploadDialogOpen(true)}>
                        Upload Version
                    </Button>
                </div>
            )}

            <ExchangeDocumentUploadDialog isOpen={isUploadDialogOpen}
                                          exchangeId={exchangeId}
                                          exchangeDocument={exchangeDocument}
                                          onDismiss={() => setIsUploadDialogOpen(false)}
                                          onDocumentUploaded={() =>
                                          {
                                              setIsUploadDialogOpen(false);
                                              fetchVersions();
                                          }}/>

            <ExchangeDocumentVersionPreviewDialog previewUrl={previewUrl}
                                                  previewVersionId={previewVersionId}
                                                  exchangeDocument={exchangeDocument}
                                                  exchange={exchange}
                                                  onClose={closePreview}/>
        </div>
    );
};

export default ExchangeDocumentVersions;
