import React from 'react';
import {
    Body1,
    Button,
    Caption1,
    Card,
    CardHeader,
    Field,
    mergeClasses,
    SearchBox,
    Tooltip
} from "@fluentui/react-components";
import {DocumentAddIcon, ZipDocumentsIcon} from "../../../components/IconBundles.tsx";
import ExchangeDocumentActionsMenu from "../exchange-document-actions-menu/ExchangeDocumentActionsMenu.tsx";
import {DocumentDetailedDto, ExchangeDetailedDto, ExchangeStatus} from "../../../models/models.tsx";
import {formatDateTimeWithOrdinal} from "../../../helpers.ts";
import {useExchangeDocumentsListStyles} from "./ExchangeDocumentsListStyles.tsx";
import {ExchangePermissions} from "../../ExchangePermissions.ts";

interface ExchangeDocumentsListProps
{
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

const ExchangeDocumentsList: React.FC<ExchangeDocumentsListProps> = (
    {
        exchangeDetails,
        filteredDocuments,
        selectedExchangeDocument,
        setSelectedExchangeDocument,
        setSelectedUpdateExchangeDocument,
        setIsUploadDocumentDialogOpen,
        onDocumentDeleted,
        onDocumentUpdated,
        onNewDocumentAdded,
        onDocumentUploaded,
        permissions,
        onFilterDocuments,
        setIsDocumentAddDialogOpen,
        setIsDocumentUpdateDialogOpen,
        setIsDocumentZipDialogOpen,
        setIsDocumentSidebarOpen
    }) =>
{
    const styles = useExchangeDocumentsListStyles();
    const isArchivedExchange =
        exchangeDetails?.status === ExchangeStatus.ENDED ||
        exchangeDetails?.status === ExchangeStatus.REJECTED;
    const canUploadInCurrentExchange = !isArchivedExchange && !!permissions?.canUploadDocument;

    const onCardClick = (exchangeDocument: DocumentDetailedDto) =>
    {
        setSelectedExchangeDocument(exchangeDocument);
    };

    const renderDocumentsActionsMenu = (exchangeDocument: DocumentDetailedDto) =>
    {
        return (
            <ExchangeDocumentActionsMenu
                exchange={exchangeDetails}
                permissions={permissions}
                onOpenDetailsSidebar={() =>
                {
                    setSelectedExchangeDocument(exchangeDocument);
                    setIsDocumentSidebarOpen(true);
                }}
                onDocumentDeleted={onDocumentDeleted}
                exchangeDocument={exchangeDocument}
                onUpload={() =>
                {
                    setSelectedExchangeDocument(exchangeDocument);
                    setIsUploadDocumentDialogOpen(true);
                }}
                onUpdate={() =>
                {
                    setSelectedUpdateExchangeDocument(exchangeDocument);
                    setIsDocumentUpdateDialogOpen(true);
                }}
                onPreviewDocument={() =>
                {
                    setSelectedExchangeDocument(exchangeDocument)
                }}
            />
        );
    };

    const getDocumentCardClasses = (exchangeDocument: DocumentDetailedDto) =>
    {
        if (exchangeDocument && exchangeDocument.id === selectedExchangeDocument?.id)
        {
            return mergeClasses(styles.documentsCard, styles.documentsCardSelected);
        }
        return styles.documentsCard;
    };

    const renderDocumentsListCard = (exchangeDocument: DocumentDetailedDto) =>
    {
        return (
            <Card key={exchangeDocument.id}
                  id={`exchange-document-card-${exchangeDocument.id}`}
                  data-doc-card="true"
                  className={getDocumentCardClasses(exchangeDocument)}
                  onClick={() => onCardClick(exchangeDocument)}>
                <CardHeader
                    header={<Body1>
                        <b>{exchangeDocument.title}</b>
                    </Body1>}
                    description={
                        <>
                            {exchangeDocument.uploadDate ? (
                                <Caption1>
                                    Uploaded {formatDateTimeWithOrdinal(exchangeDocument.uploadDate)}
                                </Caption1>
                            ) : (
                                <Button
                                    id={`exchange-document-upload-new-${exchangeDocument.id}`}
                                    appearance="transparent"
                                    size={"small"}
                                    shape={"circular"}
                                    icon={<DocumentAddIcon/>}
                                    disabled={!canUploadInCurrentExchange}
                                    onClick={() =>
                                    {
                                        if (!canUploadInCurrentExchange) return;
                                        setSelectedExchangeDocument(exchangeDocument);
                                        setIsUploadDocumentDialogOpen(true);
                                    }}>
                                    Upload new document
                                    {//ToDo: change text to upload new version when not first upload
                                    }
                                </Button>
                            )}
                        </>
                    }
                    action={<>{exchangeDetails && renderDocumentsActionsMenu(exchangeDocument)}</>}
                />
            </Card>
        );
    };

    return (
        <section className={styles.container}>
            <div className={styles.searchSection}>
                <Tooltip content="Zip all documents"
                         relationship="description">
                    <Button id="exchange-documents-zip-download"
                            size={"small"}
                            disabled={!permissions?.canDownloadDocumentsZip}
                            onClick={() => setIsDocumentZipDialogOpen(true)}
                            appearance={"transparent"}
                            shape={"circular"}
                            icon={<ZipDocumentsIcon/>}/>
                </Tooltip>
                <Field className={styles.searchField}>
                    <SearchBox id="exchange-documents-filter-input" placeholder={"Filter documents"}
                               onChange={onFilterDocuments}/>
                </Field>
            </div>
            <div id={"documents-list-cards"}
                 className={styles.cardListSection}>
                {filteredDocuments.map((document: DocumentDetailedDto) => renderDocumentsListCard(document))}
            </div>
        </section>
    );
};

export default ExchangeDocumentsList;