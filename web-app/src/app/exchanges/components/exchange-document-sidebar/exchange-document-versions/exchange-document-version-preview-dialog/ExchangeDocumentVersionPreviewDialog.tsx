import React from "react";
import {Button, Dialog, DialogBody, DialogContent, DialogSurface} from "@fluentui/react-components";
import {DismissRegular} from "@fluentui/react-icons";
import {DocumentDetailedDto, ExchangeDetailedDto} from "../../../../../models/models";
import ExchangeDocumentPreviewer from "../../../exchange-document-preview/ExchangeDocumentPreviewer.tsx";
import {useExchangeDocumentVersionPreviewDialogStyles} from "./ExchangeDocumentVersionPreviewDialogStyles.tsx";

interface ExchangeDocumentVersionPreviewDialogProps
{
    previewUrl: string | null;
    previewVersionId: string | null;
    exchangeDocument: DocumentDetailedDto;
    exchange: ExchangeDetailedDto;
    onClose: () => void;
}

const ExchangeDocumentVersionPreviewDialog: React.FC<ExchangeDocumentVersionPreviewDialogProps> = (
    {
        previewUrl,
        previewVersionId,
        exchangeDocument,
        exchange,
        onClose,
    }) =>
{
    const styles = useExchangeDocumentVersionPreviewDialogStyles();

    return (
        <Dialog open={!!previewUrl}
                onOpenChange={(_e, data) => { if (!data.open) onClose(); }}>
            <DialogSurface id={"exchange-document-versions-dialog-surface-1"}
                           className={styles.previewDialogSurface}>
                <Button id="version-preview-close-btn"
                        appearance="subtle"
                        shape="circular"
                        size="small"
                        icon={<DismissRegular/>}
                        className={styles.previewCloseButton}
                        onClick={onClose}/>
                <DialogBody id={"exchange-document-versions-dialog-body-1"}
                            className={styles.previewDialogBody}>
                    <DialogContent id={"exchange-document-versions-dialog-content-1"}
                                   className={styles.previewDialogContent}>
                        {previewUrl && (
                            <ExchangeDocumentPreviewer document={exchangeDocument}
                                                       exchange={exchange}
                                                       overridePdfUrl={previewUrl}
                                                       documentVersionId={previewVersionId ?? undefined}
                                                       hideEnlarge/>
                        )}
                    </DialogContent>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default ExchangeDocumentVersionPreviewDialog;
