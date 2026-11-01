import React from 'react';
import AddDocumentDialog from '../../exchange-document-add-dialog/ExchangeDocumentAddDialog.tsx';
import ExchangeDocumentUploadDialog from '../../exchange-document-upload-dialog/ExchangeDocumentUploadDialog.tsx';
import ExchangeDocumentUpdateDialog from '../../exchange-document-update-dialog/ExchangeDocumentUpdateDialog.tsx';
import {DocumentDetailedDto, ExchangeDetailedDto} from '../../../../models/models.tsx';
import ExchangeDocumentZipDownloadDialog
    from "../../exchange-document-zip-download-dialog/ExchangeDocumentZipDownloadDialog.tsx";
import {ExchangeDialogsState} from '../useExchangeDialogsState.ts';

export interface ExchangeDocumentDialogHandlers
{
    onNewDocumentAdded: (newExchangeDocument: DocumentDetailedDto) => void;
    onDocumentUploaded: (uploadedDocument: DocumentDetailedDto) => void;
    onDocumentUpdated: (updatedDocument: DocumentDetailedDto) => void;
}

interface ExchangeDocumentDialogsProps extends ExchangeDocumentDialogHandlers
{
    dialogs: ExchangeDialogsState;
    exchangeDetails: ExchangeDetailedDto | null;
    selectedExchangeId: string | null;
    selectedUploadExchangeDocument: DocumentDetailedDto | undefined;
    selectedUpdateExchangeDocument: DocumentDetailedDto | undefined;
    setSelectedUpdateExchangeDocument: React.Dispatch<React.SetStateAction<DocumentDetailedDto | undefined>>;
}

const ExchangeDocumentDialogs: React.FC<ExchangeDocumentDialogsProps> = (
    {
        dialogs,
        exchangeDetails,
        selectedExchangeId,
        selectedUploadExchangeDocument,
        selectedUpdateExchangeDocument,
        setSelectedUpdateExchangeDocument,
        onNewDocumentAdded,
        onDocumentUploaded,
        onDocumentUpdated,
    }) =>
{
    return (
        <>
            <AddDocumentDialog isOpen={dialogs.isDocumentAddDialogOpen}
                               onDismiss={() => dialogs.setIsDocumentAddDialogOpen(false)}
                               exchangeId={selectedExchangeId}
                               onDocumentAdded={onNewDocumentAdded}/>

            <ExchangeDocumentUploadDialog isOpen={dialogs.isUploadDocumentDialogOpen}
                                          onDismiss={() => dialogs.setIsUploadDocumentDialogOpen(false)}
                                          exchangeId={selectedExchangeId}
                                          exchangeDocument={selectedUploadExchangeDocument}
                                          onDocumentUploaded={onDocumentUploaded}/>

            <ExchangeDocumentUpdateDialog isOpen={dialogs.isUpdateDocumentDialogOpen}
                                          onDismiss={() =>
                                          {
                                              setSelectedUpdateExchangeDocument(undefined)
                                              dialogs.setIsUpdateDocumentDialogOpen(false)
                                          }}
                                          exchangeId={selectedExchangeId}
                                          exchangeDocument={selectedUpdateExchangeDocument}
                                          onDocumentUpdated={onDocumentUpdated}/>

            <ExchangeDocumentZipDownloadDialog isOpen={dialogs.isDocumentZipDialogOpen}
                                               onDismiss={() => dialogs.setIsDocumentZipDialogOpen(false)}
                                               exchange={exchangeDetails}/>
        </>
    );
};

export default ExchangeDocumentDialogs;
