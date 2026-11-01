import React from 'react';
import ExchangeDocumentsList from '../exchange-document-list/ExchangeDocumentsList.tsx';
import NoExchangeDocuments from '../exchange-documents-none/NoExchangeDocuments.tsx';
import ExchangeDocumentPreviewer from '../exchange-document-preview/ExchangeDocumentPreviewer.tsx';
import {ExchangesWorkspace} from '../../useExchangesWorkspace.ts';

interface ExchangeDocumentsSectionProps
{
    workspace: ExchangesWorkspace;
}

const ExchangeDocumentsSection: React.FC<ExchangeDocumentsSectionProps> = ({workspace}) =>
{
    const {
        exchangeDetails,
        permissions,
        dialogs,
        previewerLink,
        isDocumentToolbarVisible,
        onFilterDocuments,
        documentSearchQuery,
        filteredDocuments,
        selectedExchangeDocument,
        setSelectedExchangeDocument,
        setSelectedUploadExchangeDocument,
        setSelectedSidebarExchangeDocument,
        setSelectedUpdateExchangeDocument,
        onDocumentDeleted,
        setIsDocumentSidebarOpen,
    } = workspace;

    return (
        <>
            {(exchangeDetails?.documents?.length > 0) && (
                <ExchangeDocumentsList
                    exchangeDetails={exchangeDetails}
                    permissions={permissions}
                    isToolbarVisible={isDocumentToolbarVisible}
                    onFilterDocuments={onFilterDocuments}
                    documentSearchQuery={documentSearchQuery}
                    filteredDocuments={filteredDocuments}
                    selectedExchangeDocument={selectedExchangeDocument}
                    setSelectedExchangeDocument={setSelectedExchangeDocument}
                    setSelectedUploadExchangeDocument={setSelectedUploadExchangeDocument}
                    setSelectedSidebarExchangeDocument={setSelectedSidebarExchangeDocument}
                    setSelectedUpdateExchangeDocument={setSelectedUpdateExchangeDocument}
                    setIsUploadDocumentDialogOpen={dialogs.setIsUploadDocumentDialogOpen}
                    setIsDocumentUpdateDialogOpen={dialogs.setIsUpdateDocumentDialogOpen}
                    onDocumentDeleted={onDocumentDeleted}
                    setIsDocumentSidebarOpen={setIsDocumentSidebarOpen}
                    isPreviewMode={!!selectedExchangeDocument}
                />
            )}

            {
                exchangeDetails.documents?.length === 0 &&
                <NoExchangeDocuments
                    setIsDocumentAddDialogOpen={dialogs.setIsDocumentAddDialogOpen}/>
            }
            {selectedExchangeDocument &&
                <ExchangeDocumentPreviewer document={selectedExchangeDocument}
                                           ref={previewerLink.documentPreviewerRef}
                                           exchange={exchangeDetails}
                                           canUploadDocument={!!permissions?.canUploadDocument}
                                           onPageChange={previewerLink.setPreviewerCurrentPage}
                                           onUploadDocument={() => {
                                               setSelectedUploadExchangeDocument(selectedExchangeDocument);
                                               dialogs.setIsUploadDocumentDialogOpen(true);
                                           }}/>
            }
        </>
    );
};

export default ExchangeDocumentsSection;
