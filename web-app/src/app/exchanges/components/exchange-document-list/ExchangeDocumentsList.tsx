import React from 'react';
import {Button, Field, SearchBox, Tooltip, mergeClasses} from "@fluentui/react-components";
import {ChevronLeftRegular, ChevronRightRegular} from "@fluentui/react-icons";
import {ZipDocumentsIcon} from "../../../components/IconBundles.tsx";
import {DocumentDetailedDto, ExchangeDetailedDto, ExchangeStatus} from "../../../models/models.tsx";
import {useExchangeDocumentsListStyles} from "./ExchangeDocumentsListStyles.tsx";
import {ExchangePermissions} from "../../ExchangePermissions.ts";
import ExchangeDocumentCard from "./ExchangeDocumentCard.tsx";
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
    onDocumentUpdated: (document: DocumentDetailedDto) => void;
    onNewDocumentAdded: (document: DocumentDetailedDto) => void;
    onDocumentUploaded: (document: DocumentDetailedDto) => void;
    permissions: ExchangePermissions;
    onFilterDocuments: (event: any, data: any) => void;
    setIsDocumentAddDialogOpen: (isOpen: boolean) => void;
    setIsDocumentZipDialogOpen: (isOpen: boolean) => void;
    setIsDocumentSidebarOpen: (isOpen: boolean) => void;
}

const ExchangeDocumentsList: React.FC<ExchangeDocumentsListProps> = (props) => {
    const styles = useExchangeDocumentsListStyles();
    const {stripRef, canScrollLeft, canScrollRight, scrollByCard, onStripKeyDown} = useDocumentStrip(
        props.selectedExchangeDocument?.id,
        props.filteredDocuments.length
    );
    const isArchived = props.exchangeDetails?.status === ExchangeStatus.ENDED ||
        props.exchangeDetails?.status === ExchangeStatus.REJECTED;
    const canUpload = !isArchived && !!props.permissions?.canUploadDocument;
    const hasOverflow = canScrollLeft || canScrollRight;

    const openUpload = (document: DocumentDetailedDto) => {
        if (!canUpload) return;
        props.setSelectedExchangeDocument(document);
        props.setIsUploadDocumentDialogOpen(true);
    };

    return (
        <section id="exchange-documents-list" className={styles.container}>
            <div id="exchange-documents-search" className={styles.searchSection}>
                <Tooltip content="Zip all documents" relationship="description">
                    <Button id="exchange-documents-zip-download"
                            size="small"
                            disabled={!props.permissions?.canDownloadDocumentsZip}
                            onClick={() => props.setIsDocumentZipDialogOpen(true)}
                            appearance="transparent"
                            shape="circular"
                            icon={<ZipDocumentsIcon/>}/>
                </Tooltip>
                <Field id="exchange-documents-search-field" className={styles.searchField}>
                    <SearchBox id="exchange-documents-filter-input"
                               placeholder="Filter documents"
                               onChange={props.onFilterDocuments}/>
                </Field>
            </div>
            <div id="exchange-documents-strip-layout" className={styles.stripLayout}>
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
                    {props.filteredDocuments.map(document => (
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
