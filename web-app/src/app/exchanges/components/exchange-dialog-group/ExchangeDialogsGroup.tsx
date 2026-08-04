import React from 'react';
import ExchangeDeleteDialog from '../exchange-delete-dialog/ExchangeDeleteDialog.tsx';
import ExchangeEndDialog from '../exchange-end-dialog/ExchangeEndDialog.tsx';
import AddDocumentDialog from '../exchange-document-add-dialog/ExchangeDocumentAddDialog.tsx';
import ExchangeDocumentUploadDialog from '../exchange-document-upload-dialog/ExchangeDocumentUploadDialog.tsx';
import ExchangeDocumentUpdateDialog from '../exchange-document-update-dialog/ExchangeDocumentUpdateDialog.tsx';
import {DocumentDetailedDto, ExchangeDetailedDto} from '../../../models/models.tsx';
import ExchangeDocumentZipDownloadDialog
    from "../exchange-document-zip-download-dialog/ExchangeDocumentZipDownloadDialog.tsx";
import ExchangeEditDialog from "../exchange-edit-dialog/ExchangeEditDialog.tsx";
import ExchangeAccessManagementDialog from "../exchange-access-management-dialog/ExchangeAccessManagementDialog.tsx";
import ExchangeDetailedViewDialog from "../exchange-detailed-view-dialog/ExchangeDetailedViewDialog.tsx";
import ExchangeRescindDialog from "../exchange-rescind-dialog/ExchangeRescindDialog.tsx";

interface ExchangeDialogsGroupProps
{
    isDeletedExchangeDialogOpen: boolean;
    setIsDeletedExchangeDialogOpen: React.Dispatch<React.SetStateAction<boolean>>;
    isExchangeEndDialogOpen: boolean;
    setIsExchangeEndDialogOpen: React.Dispatch<React.SetStateAction<boolean>>;
    isExchangeRescindDialogOpen: boolean;
    setIsExchangeRescindDialogOpen: React.Dispatch<React.SetStateAction<boolean>>;
    isDocumentAddDialogOpen: boolean;
    setIsDocumentAddDialogOpen: React.Dispatch<React.SetStateAction<boolean>>;
    isUploadDocumentDialogOpen: boolean;
    setIsUploadDocumentDialogOpen: React.Dispatch<React.SetStateAction<boolean>>;
    isUpdateDocumentDialogOpen: boolean;
    setIsUpdateDocumentDialogOpen: React.Dispatch<React.SetStateAction<boolean>>;
    isDocumentZipDialogOpen: boolean;
    setIsDocumentZipDialogOpen: React.Dispatch<React.SetStateAction<boolean>>;
    isExchangeEditDialogOpen: boolean;
    setIsExchangeEditDialogOpen: React.Dispatch<React.SetStateAction<boolean>>;
    isExchangeAccessManagementDialogOpen: boolean;
    setIsExchangeAccessManagementDialogOpen: React.Dispatch<React.SetStateAction<boolean>>;
    isExchangeDetailedViewDialogOpen: boolean;
    setIsExchangeDetailedViewDialogOpen: React.Dispatch<React.SetStateAction<boolean>>;
    exchangeDetails: ExchangeDetailedDto | null;
    selectedExchangeId: string | null;
    selectedUploadExchangeDocument: DocumentDetailedDto | undefined;
    selectedUpdateExchangeDocument: DocumentDetailedDto | undefined;
    setSelectedUpdateExchangeDocument: React.Dispatch<React.SetStateAction<DocumentDetailedDto | undefined>>;
    onNewDocumentAdded: (newExchangeDocument: DocumentDetailedDto) => void;
    onDocumentUploaded: (uploadedDocument: DocumentDetailedDto) => void;
    onDocumentUpdated: (updatedDocument: DocumentDetailedDto) => void;
    onExchangeDeleted: (exchangeId: string) => void;
    onExchangeEnded: (exchange: ExchangeDetailedDto) => void;
    onExchangeRescinded: (exchange: ExchangeDetailedDto) => void;
    onExchangeEdited: (exchange: ExchangeDetailedDto) => void;
    onExchangeAccessManagementUpdated: (exchange: ExchangeDetailedDto) => void;
}

const ExchangeDialogsGroup: React.FC<ExchangeDialogsGroupProps> = (
    {
        isDeletedExchangeDialogOpen,
        setIsDeletedExchangeDialogOpen,
        isExchangeEndDialogOpen,
        setIsExchangeEndDialogOpen,
        isExchangeRescindDialogOpen,
        setIsExchangeRescindDialogOpen,
        isDocumentAddDialogOpen,
        setIsDocumentAddDialogOpen,
        isUploadDocumentDialogOpen,
        setIsUploadDocumentDialogOpen,
        isUpdateDocumentDialogOpen,
        setIsUpdateDocumentDialogOpen,
        isDocumentZipDialogOpen,
        setIsDocumentZipDialogOpen,
        isExchangeEditDialogOpen,
        setIsExchangeEditDialogOpen,
        isExchangeAccessManagementDialogOpen,
        setIsExchangeAccessManagementDialogOpen,
        isExchangeDetailedViewDialogOpen,
        setIsExchangeDetailedViewDialogOpen,
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
            <ExchangeDeleteDialog isOpen={isDeletedExchangeDialogOpen}
                                 onDismiss={() => setIsDeletedExchangeDialogOpen(false)}
                                 exchange={exchangeDetails}
                                 onExchangeDeleted={onExchangeDeleted}/>

            <ExchangeEditDialog isOpen={isExchangeEditDialogOpen}
                               onDismiss={() => setIsExchangeEditDialogOpen(false)}
                               exchange={exchangeDetails}
                               onExchangeEdited={onExchangeEdited}/>

            <ExchangeEndDialog isOpen={isExchangeEndDialogOpen}
                               onDismiss={() => setIsExchangeEndDialogOpen(false)}
                               exchange={exchangeDetails}
                               onExchangeEnded={onExchangeEnded}/>

            <ExchangeRescindDialog
                isOpen={isExchangeRescindDialogOpen}
                onDismiss={() => setIsExchangeRescindDialogOpen(false)}
                exchange={exchangeDetails}
                onExchangeRescinded={onExchangeRescinded}
            />

            <ExchangeAccessManagementDialog isOpen={isExchangeAccessManagementDialogOpen}
                                           onDismiss={() => setIsExchangeAccessManagementDialogOpen(false)}
                                           exchange={exchangeDetails}
                                           onExchangeAccessManagementUpdated={onExchangeAccessManagementUpdated}/>

            <ExchangeDetailedViewDialog isOpen={isExchangeDetailedViewDialogOpen}
                                       onDismiss={() => setIsExchangeDetailedViewDialogOpen(false)}
                                       exchange={exchangeDetails}/>

            <AddDocumentDialog isOpen={isDocumentAddDialogOpen}
                               onDismiss={() => setIsDocumentAddDialogOpen(false)}
                               exchangeId={selectedExchangeId}
                               onDocumentAdded={onNewDocumentAdded}/>

            <ExchangeDocumentUploadDialog isOpen={isUploadDocumentDialogOpen}
                                         onDismiss={() => setIsUploadDocumentDialogOpen(false)}
                                         exchangeId={selectedExchangeId}
                                         exchangeDocument={selectedUploadExchangeDocument}
                                         onDocumentUploaded={onDocumentUploaded}/>

            <ExchangeDocumentUpdateDialog isOpen={isUpdateDocumentDialogOpen}
                                         onDismiss={() =>
                                         {
                                             setSelectedUpdateExchangeDocument(undefined)
                                             setIsUpdateDocumentDialogOpen(false)
                                         }}
                                         exchangeId={selectedExchangeId}
                                         exchangeDocument={selectedUpdateExchangeDocument}
                                         onDocumentUpdated={onDocumentUpdated}/>

            <ExchangeDocumentZipDownloadDialog isOpen={isDocumentZipDialogOpen}
                                              onDismiss={() => setIsDocumentZipDialogOpen(false)}
                                              exchange={exchangeDetails}/>
        </>
    );
};

export default ExchangeDialogsGroup;
