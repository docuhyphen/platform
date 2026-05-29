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

interface DocumentActionsMenuProps
{
    session: SharingSessionDetailedDto;
    sessionDocument: DocumentDetailedDto;
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
    const canMutateDocuments =
        session?.status === SharingSessionStatus.INITIATED ||
        session?.status === SharingSessionStatus.ACCEPTED_STARTED;

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
                        {canMutateDocuments && (
                            <>
                                <MenuItem icon={<EditIcon/>}
                                          id={`session-document-action-edit-${sessionDocument.id}`}
                                          onClick={onUpdate}>
                                    Edit
                                </MenuItem>
                                <Divider/>
                                <MenuItem
                                    id={`session-document-action-upload-${sessionDocument.id}`}
                                    icon={<UploadIcon/>}
                                    onClick={onUpload}>
                                    {sessionDocument.uploadDate ? 'Re upload' : 'Upload'}
                                </MenuItem>
                            </>
                        )}
                        <MenuItem icon={<DownloadIcon/>}
                                  id={`session-document-action-download-${sessionDocument.id}`}
                                  onClick={() => setIsDownloadDocumentOpen(true)}>
                            Download
                        </MenuItem>
                        {canMutateDocuments && (
                            <MenuItem icon={<DeleteIcon/>}
                                      id={`session-document-action-delete-${sessionDocument.id}`}
                                      onClick={() => setIsDeleteDialogOpen(true)}>
                                Delete
                            </MenuItem>
                        )}
                        <Divider/>
                        <MenuItem icon={<DocumentPreviewIcon/>}
                                  id={`session-document-action-preview-${sessionDocument.id}`}
                                  onClick={() => onPreviewDocument()}>
                            Preview
                        </MenuItem>
                        {canMutateDocuments && appUserPersonOrganization &&
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