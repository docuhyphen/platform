import React from 'react';
import {Button, Divider, Menu, MenuItem, MenuList, MenuPopover, MenuTrigger} from "@fluentui/react-components";
import {MoreVerticalRegular} from "@fluentui/react-icons";
import {DocumentDetailedDto, SharingSessionDetailedDto, SharingSessionStatus} from "../../../models/models.tsx";
import SessionDocumentDeleteDialog from "../session-document-delete-dialog/SessionDocumentDeleteDialog.tsx";
import SessionDocumentDownloadDialog from "../session-document-download-dialog/SessionDocumentDownloadDialog.tsx";
import {
    DeleteIcon,
    DocumentPreviewIcon,
    DownloadIcon,
    EditIcon,
    MoreInfoIcon,
    UploadIcon
} from "../../../components/IconBundles.tsx";
import {useAuth} from "../../../../context/AuthContext.tsx";
import {SharingSessionPermissions} from "../../SessionPermissions.ts";

interface DocumentActionsMenuProps
{
    session: SharingSessionDetailedDto;
    sessionDocument: DocumentDetailedDto;
    permissions?: SharingSessionPermissions;
    onUpload: () => void;
    onUpdate: () => void;
    onOpenDetailsSidebar: () => void;
    onDocumentDeleted: (documentId: string) => void;
    onPreviewDocument: () => void;
}

const SessionDocumentActionsMenu: React.FC<DocumentActionsMenuProps> = (
    {
        session,
        sessionDocument,
        permissions,
        onUpload,
        onUpdate,
        onOpenDetailsSidebar,
        onPreviewDocument,
        onDocumentDeleted
    }) =>
{
    const {appUserPersonOrganization} = useAuth();
    const [isDeleteDialogOpen, setIsDeleteDialogOpen] = React.useState(false);
    const [isDownloadDocumentOpen, setIsDownloadDocumentOpen] = React.useState(false);

    const isActiveSession =
        session?.status === SharingSessionStatus.INITIATED ||
        session?.status === SharingSessionStatus.ACCEPTED_STARTED;

    const canUpload = isActiveSession && !!permissions?.canUploadDocument;
    const canEdit = isActiveSession && !!permissions?.canEditSessionDocument;
    const canDelete = isActiveSession && !!permissions?.canDeleteSessionDocument;
    const canDownload = !!permissions?.canDownloadDocumentsZip;

    // TEMP DEBUG — remove once permissions issue resolved
    // eslint-disable-next-line no-console
    console.log('[SessionDocumentActionsMenu]', {
        sessionId: session?.id,
        documentId: sessionDocument?.id,
        status: session?.status,
        isActiveSession,
        permissions,
        canUpload, canEdit, canDelete, canDownload,
        sessionAllowDocumentUpload: session?.allowDocumentUpload,
        sessionAllowDocumentUpdate: session?.allowDocumentUpdate,
        sessionAllowDocumentDeletion: session?.allowDocumentDeletion,
        sessionAllowDocumentDownload: session?.allowDocumentDownload,
        sessionAllowDocumentAddition: session?.allowDocumentAddition,
    });

    const stopCardClickPropagation = (event: React.MouseEvent<HTMLElement>) =>
    {
        event.stopPropagation();
    };

    return (
        <section className={"document-actions-menu"} onClick={stopCardClickPropagation}>
            <Menu positioning={{autoSize: true}}>
                <MenuTrigger disableButtonEnhancement>
                    <Button id={`session-document-actions-trigger-${sessionDocument.id}`}
                            icon={<MoreVerticalRegular/>}
                            appearance="subtle"
                            onClick={stopCardClickPropagation}/>
                </MenuTrigger>
                <MenuPopover>
                    <MenuList id={`session-document-actions-menu-${sessionDocument.id}`}>
                        <MenuItem icon={<EditIcon/>}
                                  id={`session-document-action-edit-${sessionDocument.id}`}
                                  disabled={!canEdit}
                                  onClick={onUpdate}>
                            Edit
                        </MenuItem>
                        <Divider/>
                        <MenuItem
                            id={`session-document-action-upload-${sessionDocument.id}`}
                            icon={<UploadIcon/>}
                            disabled={!canUpload}
                            onClick={onUpload}>
                            {sessionDocument.uploadDate ? 'Re upload' : 'Upload'}
                        </MenuItem>
                        <MenuItem icon={<DownloadIcon/>}
                                  id={`session-document-action-download-${sessionDocument.id}`}
                                  disabled={!canDownload}
                                  onClick={() => setIsDownloadDocumentOpen(true)}>
                            Download
                        </MenuItem>
                        <MenuItem icon={<DeleteIcon/>}
                                  id={`session-document-action-delete-${sessionDocument.id}`}
                                  disabled={!canDelete}
                                  onClick={() => setIsDeleteDialogOpen(true)}>
                            Delete
                        </MenuItem>
                        <Divider/>
                        <MenuItem icon={<DocumentPreviewIcon/>}
                                  id={`session-document-action-preview-${sessionDocument.id}`}
                                  disabled={!sessionDocument.uploadDate}
                                  onClick={() => onPreviewDocument()}>
                            Preview
                        </MenuItem>
                        {isActiveSession && appUserPersonOrganization &&
                            <MenuItem icon={<MoreInfoIcon/>}
                                      id={`session-document-action-details-${sessionDocument.id}`}
                                      onClick={() => onOpenDetailsSidebar()}>
                                Details
                            </MenuItem>
                        }
                    </MenuList>
                </MenuPopover>
            </Menu>

            <SessionDocumentDeleteDialog sessionDocument={sessionDocument}
                                         session={session}
                                         isOpen={isDeleteDialogOpen}
                                         onClose={() => setIsDeleteDialogOpen(false)}
                                         onDocumentDeleted={onDocumentDeleted}/>

            <SessionDocumentDownloadDialog sessionDocument={sessionDocument}
                                           session={session}
                                           isOpen={isDownloadDocumentOpen}
                                           onDismiss={() => setIsDownloadDocumentOpen(false)}/>
        </section>
    );
};

export default SessionDocumentActionsMenu;