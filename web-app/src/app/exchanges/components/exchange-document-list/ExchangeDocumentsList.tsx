import React from "react";
import {Button, mergeClasses} from "@fluentui/react-components";
import {ChevronLeftRegular, ChevronRightRegular} from "@fluentui/react-icons";
import {DocumentDetailedDto, ExchangeDetailedDto, ExchangeStatus} from "../../../models/models.tsx";
import {useExchangeDocumentsListStyles} from "./ExchangeDocumentsListStyles.tsx";
import {ExchangePermissions} from "../../ExchangePermissions.ts";
import ExchangeDocumentCard from "./ExchangeDocumentCard.tsx";
import ExchangeDocumentToolbar, {
    DocumentSortOption,
    DocumentStatusFilter
} from "./ExchangeDocumentToolbar.tsx";
import {useDocumentStrip} from "./useDocumentStrip.ts";

interface ExchangeDocumentsListProps {
    exchangeDetails: ExchangeDetailedDto | null;
    filteredDocuments: DocumentDetailedDto[];
    selectedExchangeDocument?: DocumentDetailedDto;
    setSelectedExchangeDocument: (document: DocumentDetailedDto) => void;
    setSelectedUpdateExchangeDocument: (document: DocumentDetailedDto) => void;
    setIsUploadDocumentDialogOpen: (isOpen: boolean) => void;
    setIsDocumentUpdateDialogOpen: (isOpen: boolean) => void;
    onDocumentDeleted: (documentId: string) => void;
    permissions: ExchangePermissions;
    onFilterDocuments: (event: any, data: any) => void;
    setIsDocumentSidebarOpen: (isOpen: boolean) => void;
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
    const {stripRef, canScrollLeft, canScrollRight, scrollByCard, onStripKeyDown} = useDocumentStrip(
        props.selectedExchangeDocument?.id,
        visibleDocuments.length
    );
    const isArchived = props.exchangeDetails?.status === ExchangeStatus.ENDED ||
        props.exchangeDetails?.status === ExchangeStatus.REJECTED ||
        props.exchangeDetails?.status === ExchangeStatus.RESCINDED;
    const canUpload = !isArchived && !!props.permissions?.canUploadDocument;
    const hasOverflow = canScrollLeft || canScrollRight;

    React.useEffect(() => {
        setStatusFilter("all");
        setSortOption("default");
    }, [props.exchangeDetails?.id]);

    const openUpload = (document: DocumentDetailedDto) => {
        if (!canUpload) return;
        props.setSelectedExchangeDocument(document);
        props.setIsUploadDocumentDialogOpen(true);
    };

    return (
        <section id="exchange-documents-list"
                 className={styles.container}>
            <ExchangeDocumentToolbar totalCount={allDocuments.length}
                                     uploadedCount={uploadedCount}
                                     activeFilter={statusFilter}
                                     sortOption={sortOption}
                                     onSearchChange={props.onFilterDocuments}
                                     onFilterChange={setStatusFilter}
                                     onSortChange={setSortOption}/>
            <div id="exchange-documents-strip-layout"
                 className={styles.stripLayout}>
                {hasOverflow && (
                    <Button id="exchange-documents-scroll-left"
                            aria-label="Scroll documents left"
                            className={styles.scrollButton}
                            appearance="subtle"
                            shape="circular"
                            disabled={!canScrollLeft}
                            icon={<ChevronLeftRegular/>}
                            onClick={() => scrollByCard(-1)}/>
                )}
                <div id="documents-list-cards"
                     ref={stripRef}
                     className={mergeClasses(styles.cardListSection, !hasOverflow && styles.cardListSectionFullWidth)}
                     tabIndex={0}
                     aria-label="Exchange documents"
                     onKeyDown={onStripKeyDown}>
                    {visibleDocuments.map(document => (
                        <ExchangeDocumentCard key={document.id}
                                              document={document}
                                              exchange={props.exchangeDetails}
                                              permissions={props.permissions}
                                              selected={document.id === props.selectedExchangeDocument?.id}
                                              canUpload={canUpload}
                                              onSelect={() => props.setSelectedExchangeDocument(document)}
                                              onUpload={() => openUpload(document)}
                                              onUpdate={() => {
                                                  props.setSelectedUpdateExchangeDocument(document);
                                                  props.setIsDocumentUpdateDialogOpen(true);
                                              }}
                                              onOpenDetails={() => {
                                                  props.setSelectedExchangeDocument(document);
                                                  props.setIsDocumentSidebarOpen(true);
                                              }}
                                              onDelete={props.onDocumentDeleted}/>
                    ))}
                </div>
                {hasOverflow && (
                    <Button id="exchange-documents-scroll-right"
                            aria-label="Scroll documents right"
                            className={styles.scrollButton}
                            appearance="subtle"
                            shape="circular"
                            disabled={!canScrollRight}
                            icon={<ChevronRightRegular/>}
                            onClick={() => scrollByCard(1)}/>
                )}
            </div>
        </section>
    );
};

export default ExchangeDocumentsList;
