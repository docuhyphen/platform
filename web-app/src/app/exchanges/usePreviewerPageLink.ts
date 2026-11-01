import {useCallback, useRef, useState} from 'react';
import {DocumentDetailedDto} from '../models/models.tsx';
import {ExchangeDocumentPreviewerHandle} from './components/exchange-document-preview/ExchangeDocumentPreviewer.tsx';

export const usePreviewerPageLink = (
    selectedExchangeDocument: DocumentDetailedDto | undefined,
    selectedSidebarExchangeDocument: DocumentDetailedDto | undefined,
) =>
{
    const documentPreviewerRef = useRef<ExchangeDocumentPreviewerHandle>(null);
    const [previewerCurrentPage, setPreviewerCurrentPage] = useState<number | undefined>(undefined);
    const canNavigatePreviewerToPage = Boolean(
        selectedExchangeDocument &&
        selectedExchangeDocument.id === selectedSidebarExchangeDocument?.id
    );
    const sidebarLinkablePageNumber = canNavigatePreviewerToPage ? previewerCurrentPage : undefined;
    const handleNavigatePreviewerToPage = useCallback((pageNumber: number) =>
    {
        documentPreviewerRef.current?.goToPage(pageNumber);
    }, []);

    return {
        documentPreviewerRef,
        setPreviewerCurrentPage,
        canNavigatePreviewerToPage,
        sidebarLinkablePageNumber,
        handleNavigatePreviewerToPage,
    };
};

export type PreviewerPageLink = ReturnType<typeof usePreviewerPageLink>;
