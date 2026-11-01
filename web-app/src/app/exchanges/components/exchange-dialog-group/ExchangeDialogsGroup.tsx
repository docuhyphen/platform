import React from 'react';
import {DocumentDetailedDto, ExchangeDetailedDto} from '../../../models/models.tsx';
import {ExchangeDialogsState} from './useExchangeDialogsState.ts';
import ExchangeLifecycleDialogs, {
    ExchangeLifecycleDialogHandlers
} from './exchange-lifecycle-dialogs/ExchangeLifecycleDialogs.tsx';
import ExchangeDocumentDialogs, {
    ExchangeDocumentDialogHandlers
} from './exchange-document-dialogs/ExchangeDocumentDialogs.tsx';

interface ExchangeDialogsGroupProps extends ExchangeLifecycleDialogHandlers, ExchangeDocumentDialogHandlers
{
    dialogs: ExchangeDialogsState;
    exchangeDetails: ExchangeDetailedDto | null;
    selectedExchangeId: string | null;
    selectedUploadExchangeDocument: DocumentDetailedDto | undefined;
    selectedUpdateExchangeDocument: DocumentDetailedDto | undefined;
    setSelectedUpdateExchangeDocument: React.Dispatch<React.SetStateAction<DocumentDetailedDto | undefined>>;
}

const ExchangeDialogsGroup: React.FC<ExchangeDialogsGroupProps> = (
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
        onExchangeDeleted,
        onExchangeEnded,
        onExchangeRescinded,
        onExchangeEdited,
        onExchangeAccessManagementUpdated,
    }) =>
{
    return (
        <>
            <ExchangeLifecycleDialogs dialogs={dialogs}
                                      exchangeDetails={exchangeDetails}
                                      onExchangeDeleted={onExchangeDeleted}
                                      onExchangeEnded={onExchangeEnded}
                                      onExchangeRescinded={onExchangeRescinded}
                                      onExchangeEdited={onExchangeEdited}
                                      onExchangeAccessManagementUpdated={onExchangeAccessManagementUpdated}/>

            <ExchangeDocumentDialogs dialogs={dialogs}
                                     exchangeDetails={exchangeDetails}
                                     selectedExchangeId={selectedExchangeId}
                                     selectedUploadExchangeDocument={selectedUploadExchangeDocument}
                                     selectedUpdateExchangeDocument={selectedUpdateExchangeDocument}
                                     setSelectedUpdateExchangeDocument={setSelectedUpdateExchangeDocument}
                                     onNewDocumentAdded={onNewDocumentAdded}
                                     onDocumentUploaded={onDocumentUploaded}
                                     onDocumentUpdated={onDocumentUpdated}/>
        </>
    );
};

export default ExchangeDialogsGroup;
