import React, {useEffect} from 'react';
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
    const [isDeleteDialogOpen, setIsDeleteDialogOpen] = React.useState(false);
    const [isDownloadDocumentOpen, setIsDownloadDocumentOpen] = React.useState(false);
    const [isSessionEnded, setIsSessionEnded] = React.useState(false);

    useEffect(() =>
    {
        if (session)
        {
            setIsSessionEnded(session.status == SharingSessionStatus.ENDED)
        }

    }, [session]);

    return (
        <>
            <Menu positioning={{autoSize: true}}>
                <MenuTrigger disableButtonEnhancement>
                    <Button icon={<MoreVerticalRegular/>} appearance="subtle"/>
                </MenuTrigger>
                <MenuPopover>
                    <MenuList>
                        <MenuItem icon={<EditIcon/>}
                                  disabled={isSessionEnded}
                                  onClick={onUpdate}>
                            Edit
                        </MenuItem>
                        <Divider/>
                        <MenuItem
                            icon={<UploadIcon/>}
                            disabled={isSessionEnded}
                            onClick={onUpload}>
                            Upload
                        </MenuItem>
                        <MenuItem icon={<DownloadIcon/>}
                                  onClick={() => setIsDownloadDocumentOpen(true)}>
                            Download
                        </MenuItem>
                        <MenuItem icon={<DeleteIcon/>}
                                  disabled={isSessionEnded}
                                  onClick={() => setIsDeleteDialogOpen(true)}>
                            Delete
                        </MenuItem>
                        <Divider/>
                        <MenuItem icon={<DocumentPreviewIcon/>}
                                  onClick={() => onPreviewDocument()}>
                            Preview
                        </MenuItem>
                        <MenuItem icon={<MoreInfoIcon/>}
                                  onClick={() => onOpenDetailsSidebar()}>
                            More info
                        </MenuItem>
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
        </>
    );
};

export default SessionDocumentActionsMenu;