import React from 'react';
import SessionDeleteDialog from '../session-delete-dialog/SessionDeleteDialog.tsx';
import SessionEndDialog from '../session-end-dialog/SessionEndDialog.tsx';
import AddDocumentDialog from '../session-document-add-dialog/SessionDocumentAddDialog.tsx';
import SessionDocumentUploadDialog from '../session-document-upload-dialog/SessionDocumentUploadDialog.tsx';
import SessionDocumentUpdateDialog from '../session-document-update-dialog/SessionDocumentUpdateDialog.tsx';
import { DocumentDetailedDto, SharingSessionDetailedDto } from '../../models/models.tsx';

interface SessionDialogsGroupProps {
    isDeletedSessionDialogOpen: boolean;
    setIsDeletedSessionDialogOpen: React.Dispatch<React.SetStateAction<boolean>>;
    isSessionEndDialogOpen: boolean;
    setIsSessionEndDialogOpen: React.Dispatch<React.SetStateAction<boolean>>;
    isDocumentAddDialogOpen: boolean;
    setIsDocumentAddDialogOpen: React.Dispatch<React.SetStateAction<boolean>>;
    isUploadDocumentDialogOpen: boolean;
    setIsUploadDocumentDialogOpen: React.Dispatch<React.SetStateAction<boolean>>;
    isUpdateDocumentDialogOpen: boolean;
    setIsUpdateDocumentDialogOpen: React.Dispatch<React.SetStateAction<boolean>>;
    sessionDetails: SharingSessionDetailedDto | null;
    selectedSessionId: string | null;
    selectedSessionDocument: DocumentDetailedDto | undefined;
    setSelectedUpdateSessionDocument: React.Dispatch<React.SetStateAction<DocumentDetailedDto | undefined>>;
    onNewDocumentAdded: (newSessionDocument: DocumentDetailedDto) => void;
    onDocumentUploaded: (uploadedDocument: DocumentDetailedDto) => void;
    onDocumentUpdated: (updatedDocument: DocumentDetailedDto) => void;
    onSessionDeleted: (sessionId: string) => void;
    onSessionEnded: (session: SharingSessionDetailedDto) => void;
}

const SessionDialogsGroup: React.FC<SessionDialogsGroupProps> = ({
                                                                     isDeletedSessionDialogOpen,
                                                                     setIsDeletedSessionDialogOpen,
                                                                     isSessionEndDialogOpen,
                                                                     setIsSessionEndDialogOpen,
                                                                     isDocumentAddDialogOpen,
                                                                     setIsDocumentAddDialogOpen,
                                                                     isUploadDocumentDialogOpen,
                                                                     setIsUploadDocumentDialogOpen,
                                                                     isUpdateDocumentDialogOpen,
                                                                     setIsUpdateDocumentDialogOpen,
                                                                     sessionDetails,
                                                                     selectedSessionId,
                                                                     selectedSessionDocument,
                                                                     setSelectedUpdateSessionDocument,
                                                                     onNewDocumentAdded,
                                                                     onDocumentUploaded,
                                                                     onDocumentUpdated,
                                                                     onSessionDeleted,
                                                                     onSessionEnded
                                                                 }) => {
    return (
        <>
            <SessionDeleteDialog isOpen={isDeletedSessionDialogOpen}
                                 onDismiss={() => setIsDeletedSessionDialogOpen(false)}
                                 session={sessionDetails}
                                 onSessionDeleted={onSessionDeleted}/>

            <SessionEndDialog isOpen={isSessionEndDialogOpen}
                              onDismiss={() => setIsSessionEndDialogOpen(false)}
                              session={sessionDetails}
                              onSessionEnded={onSessionEnded}/>

            <AddDocumentDialog isOpen={isDocumentAddDialogOpen}
                               onDismiss={() => setIsDocumentAddDialogOpen(false)}
                               sessionId={selectedSessionId}
                               onDocumentAdded={onNewDocumentAdded}/>

            <SessionDocumentUploadDialog isOpen={isUploadDocumentDialogOpen}
                                         onDismiss={() => setIsUploadDocumentDialogOpen(false)}
                                         sessionId={selectedSessionId}
                                         sessionDocument={selectedSessionDocument}
                                         onDocumentUploaded={onDocumentUploaded}/>

            <SessionDocumentUpdateDialog isOpen={isUpdateDocumentDialogOpen}
                                         onDismiss={() => {
                                             setSelectedUpdateSessionDocument(undefined)
                                             setIsUpdateDocumentDialogOpen(false)
                                         }}
                                         sessionId={selectedSessionId}
                                         sessionDocument={selectedSessionDocument}
                                         onDocumentUpdated={onDocumentUpdated}/>
        </>
    );
};

export default SessionDialogsGroup;