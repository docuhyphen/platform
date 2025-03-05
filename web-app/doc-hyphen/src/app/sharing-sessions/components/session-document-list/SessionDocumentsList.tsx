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
import SessionDocumentActionsMenu from "../session-document-actions-menu/SessionDocumentActionsMenu.tsx";
import {DocumentDetailedDto, SharingSessionDetailedDto} from "../../../models/models.tsx";
import {formatDateTimeWithOrdinal} from "../../../helpers.ts";
import {useSessionDocumentsListStyles} from "./SessionDocumentsListStyles.tsx";
import {SharingSessionPermissions} from "../../SessionPermissions.ts";

interface SessionDocumentsListProps
{
    sessionDetails: SharingSessionDetailedDto | null;
    filteredDocuments: DocumentDetailedDto[];
    setSelectedSessionDocument: (document: DocumentDetailedDto) => void;
    setSelectedUpdateSessionDocument: (document: DocumentDetailedDto) => void;
    setIsUploadDocumentDialogOpen: (isOpen: boolean) => void;
    setIsDocumentUpdateDialogOpen: (isOpen: boolean) => void;
    onDocumentDeleted: (documentId: string) => void;
    onDocumentUpdated: (document: DocumentDetailedDto) => void;
    onNewDocumentAdded: (document: DocumentDetailedDto) => void;
    onDocumentUploaded: (document: DocumentDetailedDto) => void;
    permissions: SharingSessionPermissions;
    onFilterDocuments: (event: any, data: any) => void;
    setIsDocumentAddDialogOpen: (isOpen: boolean) => void;
    setIsDocumentZipDialogOpen: (isOpen: boolean) => void;
}

const SessionDocumentsList: React.FC<SessionDocumentsListProps> = (
    {
        sessionDetails,
        filteredDocuments,
        setSelectedSessionDocument,
        setSelectedUpdateSessionDocument,
        setIsUploadDocumentDialogOpen,
        onDocumentDeleted,
        onDocumentUpdated,
        onNewDocumentAdded,
        onDocumentUploaded,
        permissions,
        onFilterDocuments,
        setIsDocumentAddDialogOpen,
        setIsDocumentUpdateDialogOpen,
        setIsDocumentZipDialogOpen
    }) =>
{
    const styles = useSessionDocumentsListStyles();

    const renderDocumentsActionsMenu = (sessionDocument: DocumentDetailedDto) =>
    {
        return (
            <SessionDocumentActionsMenu
                session={sessionDetails}
                onOpenDetailsSidebar={() =>
                {
                    setSelectedSessionDocument(sessionDocument);
                }}
                onDocumentDeleted={onDocumentDeleted}
                sessionDocument={sessionDocument}
                onUpload={() =>
                {
                    setSelectedSessionDocument(sessionDocument);
                    setIsUploadDocumentDialogOpen(true);
                }}
                onUpdate={() =>
                {
                    setSelectedUpdateSessionDocument(sessionDocument);
                    setIsDocumentUpdateDialogOpen(true);
                }}
                onPreviewDocument={() => setSelectedSessionDocument(sessionDocument)}
            />
        );
    };

    const getDocumentCardClasses = (sessionDocument: DocumentDetailedDto) =>
    {
        if (sessionDocument && sessionDocument.id === setSelectedSessionDocument?.id)
        {
            return mergeClasses(styles.documentsCard, styles.documentsCardSelected);
        }
        return styles.documentsCard;
    };

    const renderDocumentsListCard = (sessionDocument: DocumentDetailedDto) =>
    {
        return (
            <Card key={sessionDocument.id}
                  className={getDocumentCardClasses(sessionDocument)}>
                <CardHeader
                    header={<Body1>
                        <b>{sessionDocument.title}</b>
                    </Body1>}
                    description={
                        <>
                            {sessionDocument.uploadDate ? (
                                <Caption1>
                                    Uploaded {formatDateTimeWithOrdinal(sessionDocument.uploadDate)}
                                </Caption1>
                            ) : (
                                <Button appearance="transparent"
                                        icon={<DocumentAddIcon/>} onClick={() =>
                                {
                                    setSelectedSessionDocument(sessionDocument);
                                    setIsUploadDocumentDialogOpen(true);
                                }}>
                                    Upload new document
                                </Button>
                            )}
                        </>
                    }
                    action={<>{sessionDetails && renderDocumentsActionsMenu(sessionDocument)}</>}
                />
            </Card>
        );
    };

    return (
        <section className={styles.container}>
            <div className={styles.searchSection}>
                <Tooltip content="Zip all documents"
                         relationship="description">
                    <Button size={"small"} disabled={!permissions?.canDownloadDocumentsZip}
                            onClick={() => setIsDocumentZipDialogOpen(true)} appearance={"transparent"}
                            icon={<ZipDocumentsIcon/>}/>
                </Tooltip>
                <Field className={styles.searchField}>
                    <SearchBox placeholder={"Filter documents"}
                               onChange={onFilterDocuments}/>
                </Field>
            </div>
            <div id={"documentsListCards"}
                 className={styles.cardListSection}>
                {filteredDocuments.map((document: DocumentDetailedDto) => renderDocumentsListCard(document))}
            </div>
        </section>
    );
};

export default SessionDocumentsList;