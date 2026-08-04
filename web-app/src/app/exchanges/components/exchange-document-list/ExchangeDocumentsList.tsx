import React from "react";
import {InputOnChangeData, SearchBoxChangeEvent} from "@fluentui/react-components";
import {DocumentDetailedDto, ExchangeDetailedDto, ExchangeStatus} from "../../../models/models.tsx";
import {useExchangeDocumentsListStyles} from "./ExchangeDocumentsListStyles.tsx";
import {ExchangePermissions} from "../../ExchangePermissions.ts";
import ExchangeDocumentCard from "./ExchangeDocumentCard.tsx";
import {DocumentSortOption, DocumentStatusFilter} from "./ExchangeDocumentToolbar.tsx";
import ExchangeDocumentBrowseView from "./ExchangeDocumentBrowseView.tsx";
import ExchangeDocumentPreviewStrip from "./ExchangeDocumentPreviewStrip.tsx";
import ExchangeDocumentToolbarPanel from "./ExchangeDocumentToolbarPanel.tsx";

interface ExchangeDocumentsListProps {
    exchangeDetails: ExchangeDetailedDto | null;
    filteredDocuments: DocumentDetailedDto[];
    documentSearchQuery: string;
    selectedExchangeDocument?: DocumentDetailedDto;
    setSelectedExchangeDocument: (document: DocumentDetailedDto) => void;
    setSelectedUploadExchangeDocument: (document: DocumentDetailedDto) => void;
    setSelectedSidebarExchangeDocument: (document: DocumentDetailedDto) => void;
    setSelectedUpdateExchangeDocument: (document: DocumentDetailedDto) => void;
    setIsUploadDocumentDialogOpen: (isOpen: boolean) => void;
    setIsDocumentUpdateDialogOpen: (isOpen: boolean) => void;
    onDocumentDeleted: (documentId: string) => void;
    permissions: ExchangePermissions;
    onFilterDocuments: (event: SearchBoxChangeEvent, data: InputOnChangeData) => void;
    setIsDocumentSidebarOpen: (isOpen: boolean) => void;
    isToolbarVisible: boolean;
    isPreviewMode: boolean;
}

const sortDocuments = (documents: DocumentDetailedDto[], sortOption: DocumentSortOption) => {
    const sorted = [...documents];
    if (sortOption === "not-uploaded") {
        return sorted.sort((first, second) => Number(!!first.uploadDate) - Number(!!second.uploadDate));
    }
    if (sortOption === "recent") {
        return sorted.sort((first, second) =>
            (Date.parse(second.uploadDate || "") || 0) - (Date.parse(first.uploadDate || "") || 0));
    }
    if (sortOption === "name" || sortOption === "name-desc") {
        const direction = sortOption === "name" ? 1 : -1;
        return sorted.sort((first, second) =>
            (first.title || "").localeCompare(second.title || "") * direction);
    }
    return sorted;
};

const ExchangeDocumentsList: React.FC<ExchangeDocumentsListProps> = (props) => {
    const styles = useExchangeDocumentsListStyles();
    const [statusFilter, setStatusFilter] = React.useState<DocumentStatusFilter>("all");
    const [sortOption, setSortOption] = React.useState<DocumentSortOption>("default");
    const allDocuments = props.exchangeDetails?.documents || [];
    const uploadedCount = allDocuments.filter(document => !!document.uploadDate).length;
    const visibleDocuments = React.useMemo(() => {
        const statusFiltered = props.filteredDocuments.filter(document =>
            statusFilter === "all" || (statusFilter === "uploaded") === !!document.uploadDate);
        return sortDocuments(statusFiltered, sortOption);
    }, [props.filteredDocuments, sortOption, statusFilter]);
    const isArchived = props.exchangeDetails?.status === ExchangeStatus.ENDED ||
        props.exchangeDetails?.status === ExchangeStatus.REJECTED ||
        props.exchangeDetails?.status === ExchangeStatus.RESCINDED;
    const canUpload = !isArchived && !!props.permissions?.canUploadDocument;
    React.useEffect(() => {
        setStatusFilter("all");
        setSortOption("default");
    }, [props.exchangeDetails?.id]);

    const openUpload = (document: DocumentDetailedDto) => {
        if (!canUpload) return;
        if (props.isPreviewMode) props.setSelectedExchangeDocument(document);
        props.setSelectedUploadExchangeDocument(document);
        props.setIsUploadDocumentDialogOpen(true);
    };

    const renderDocumentCard = (document: DocumentDetailedDto) => (
        <ExchangeDocumentCard key={document.id}
                              document={document}
                              exchange={props.exchangeDetails}
                              permissions={props.permissions}
                              selected={document.id === props.selectedExchangeDocument?.id}
                              canUpload={canUpload}
                              showThumbnail={!props.isPreviewMode}
                              onSelect={() => props.setSelectedExchangeDocument(document)}
                              onUpload={() => openUpload(document)}
                              onUpdate={() => {
                                  props.setSelectedUpdateExchangeDocument(document);
                                  props.setIsDocumentUpdateDialogOpen(true);
                              }}
                              onOpenDetails={() => {
                                  if (props.isPreviewMode) props.setSelectedExchangeDocument(document);
                                  props.setSelectedSidebarExchangeDocument(document);
                                  props.setIsDocumentSidebarOpen(true);
                              }}
                              onDelete={props.onDocumentDeleted}/>
    );

    return (
        <section id="exchange-documents-list"
                 className={styles.container}>
            <ExchangeDocumentToolbarPanel visible={props.isToolbarVisible}
                                          totalCount={allDocuments.length}
                                          uploadedCount={uploadedCount}
                                          activeFilter={statusFilter}
                                          sortOption={sortOption}
                                          searchQuery={props.documentSearchQuery}
                                          onSearchChange={props.onFilterDocuments}
                                          onFilterChange={setStatusFilter}
                                          onSortChange={setSortOption}/>
            {!props.isPreviewMode && (
                <ExchangeDocumentBrowseView documents={visibleDocuments}
                                            renderDocument={renderDocumentCard}/>
            )}
            {props.isPreviewMode && (
                <ExchangeDocumentPreviewStrip documents={visibleDocuments}
                                              selectedDocumentId={props.selectedExchangeDocument?.id}
                                              renderDocument={renderDocumentCard}/>
            )}
        </section>
    );
};

export default ExchangeDocumentsList;
