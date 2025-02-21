import React from 'react';
import {Button, Divider, Menu, MenuItem, MenuList, MenuPopover, MenuTrigger} from "@fluentui/react-components";
import {
    ArrowDownloadFilled,
    ArrowDownloadRegular,
    ArrowUploadFilled,
    ArrowUploadRegular,
    bundleIcon,
    DeleteFilled,
    DeleteRegular,
    InfoFilled,
    InfoRegular,
    MoreVerticalRegular,
    NotepadEditFilled,
    NotepadEditRegular
} from "@fluentui/react-icons";
import {DocumentDetailedDto, SharingSessionDetailedDto} from "../../../models/models.tsx";
import SessionDocumentDeleteDialog from "../session-document-delete-dialog/SessionDocumentDeleteDialog.tsx";
import SessionDocumentDownloadDialog from "../session-document-download-dialog/SessionDocumentDownloadDialog.tsx";


interface DocumentActionsMenuProps
{
    session: SharingSessionDetailedDto;
    sessionDocument: DocumentDetailedDto;
    onUpload: () => void;
    onUpdate: () => void;
    onOpenDetailsSidebar: () => void;
    onDocumentDeleted: (documentId: string) => void;
}

const DocumentActionsMenu: React.FC<DocumentActionsMenuProps> = (
    {
        session,
        sessionDocument,
        onUpload,
        onUpdate,
        onOpenDetailsSidebar,
        onDocumentDeleted
    }) =>
{
    const [isDeleteDialogOpen, setIsDeleteDialogOpen] = React.useState(false);
    const [isDownloadDocumentOpen, setIsDownloadDocumentOpen] = React.useState(false);

    const EditIcon = bundleIcon(NotepadEditFilled, NotepadEditRegular)
    const UploadIcon = bundleIcon(ArrowUploadFilled, ArrowUploadRegular)
    const DownloadIcon = bundleIcon(ArrowDownloadFilled, ArrowDownloadRegular)
    const DeleteIcon = bundleIcon(DeleteFilled, DeleteRegular)
    const MoreInfoIcon = bundleIcon(InfoFilled, InfoRegular)

    return (
        <>
            <Menu positioning={{autoSize: true}}>
                <MenuTrigger disableButtonEnhancement>
                    <Button icon={<MoreVerticalRegular/>} appearance="subtle"/>
                </MenuTrigger>
                <MenuPopover>
                    <MenuList>
                        <MenuItem icon={<EditIcon/>}
                                  onClick={onUpdate}>Edit</MenuItem>
                        <Divider/>
                        <MenuItem
                            icon={<UploadIcon/>}
                            onClick={onUpload}>
                            Upload
                        </MenuItem>
                        <MenuItem icon={<DownloadIcon/>}
                                  onClick={setIsDownloadDocumentOpen}>Download</MenuItem>
                        {/*<MenuItem icon={<DocumentPrintRegular/>}*/}
                        {/*          onClick={handlePrint}>Print</MenuItem>*/}
                        <MenuItem icon={<DeleteIcon/>} onClick={() =>
                        {
                            console.log("sessionDocument", sessionDocument)
                            setIsDeleteDialogOpen(true)
                        }}
                        >Delete</MenuItem>
                        <Divider/>
                        <MenuItem icon={<MoreInfoIcon/>}
                                  onClick={() =>
                                      onOpenDetailsSidebar()}>More info</MenuItem>
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

export default DocumentActionsMenu;