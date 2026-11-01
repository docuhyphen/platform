import {useEffect} from 'react';
import {InputOnChangeData, SearchBoxChangeEvent} from '@fluentui/react-components';
import {DocumentDetailedDto} from '../models/models.tsx';
import {resolvePreviewDocumentSelection} from './exchangeDocumentSelection.ts';
import {ExchangeWorkspaceState} from './useExchangeWorkspaceState.ts';
import {ExchangeDialogsState} from './components/exchange-dialog-group/useExchangeDialogsState.ts';

export const useExchangeDocumentHandlers = (
    state: ExchangeWorkspaceState,
    dialogs: ExchangeDialogsState,
    autoPreviewDocuments: boolean,
) =>
{
    const {
        selectedExchangeId,
        exchangeDetails,
        setExchangeDetails,
        documentSearchQuery,
        setDocumentSearchQuery,
        setFilteredDocuments,
        setSelectedExchangeDocument,
        setSelectedUploadExchangeDocument,
        setSelectedSidebarExchangeDocument,
        setIsDocumentSidebarOpen,
        setDetailsActiveTab,
        setIsDocumentToolbarVisible,
    } = state;

    useEffect(() =>
    {
        setDocumentSearchQuery("");
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [selectedExchangeId]);

    useEffect(() =>
    {
        const documents = exchangeDetails?.documents || [];
        const normalizedQuery = documentSearchQuery.trim().toLowerCase();

        if (normalizedQuery === "")
        {
            setFilteredDocuments(documents);
            return;
        }

        setFilteredDocuments(
            documents.filter(document => document.title.toLowerCase().includes(normalizedQuery))
        );
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [documentSearchQuery, exchangeDetails?.documents]);

    useEffect(() =>
    {
        setIsDocumentSidebarOpen(false);
        setSelectedExchangeDocument(undefined);
        setSelectedUploadExchangeDocument(undefined);
        setSelectedSidebarExchangeDocument(undefined);
        setDetailsActiveTab('documents');
        setIsDocumentToolbarVisible(false);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [selectedExchangeId]);

    const applyUpdatedDocuments = (updatedDocuments: DocumentDetailedDto[] | undefined) =>
    {
        setExchangeDetails({...exchangeDetails, documents: updatedDocuments});
        setFilteredDocuments(updatedDocuments || []);
        setSelectedExchangeDocument((currentSelection) =>
            resolvePreviewDocumentSelection(
                updatedDocuments || [],
                currentSelection?.id,
                autoPreviewDocuments
            )
        );
    };

    const onDocumentDeleted = (documentId: string) =>
    {
        if (exchangeDetails)
        {
            dialogs.setIsDocumentAddDialogOpen(false);
            applyUpdatedDocuments(exchangeDetails.documents?.filter(document => document.id !== documentId));
        }
    };

    const onNewDocumentAdded = (newExchangeDocument: DocumentDetailedDto) =>
    {
        if (exchangeDetails)
        {
            const currentDocuments = (exchangeDetails.documents && exchangeDetails.documents.length)
                ? exchangeDetails.documents
                : [];
            applyUpdatedDocuments([...currentDocuments, newExchangeDocument]);
        }
    };

    const onDocumentUpdated = (updatedDocument: DocumentDetailedDto) =>
    {
        if (exchangeDetails)
        {
            applyUpdatedDocuments(exchangeDetails.documents?.map(document =>
                document.id === updatedDocument.id ? {...document, ...updatedDocument} : document
            ));
        }
    };

    const onDocumentUploaded = (uploadedDocument: DocumentDetailedDto) =>
    {
        onDocumentUpdated(uploadedDocument);
    };

    const onFilterDocuments = (_event: SearchBoxChangeEvent, data: InputOnChangeData) =>
    {
        setDocumentSearchQuery(data.value);
    };

    return {onDocumentDeleted, onNewDocumentAdded, onDocumentUpdated, onDocumentUploaded, onFilterDocuments};
};
