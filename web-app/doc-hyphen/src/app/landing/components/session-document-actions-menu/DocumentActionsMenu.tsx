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
import useToken from "../../../../context/useToken.tsx";
import {downloadSharingSessionDocument} from "../../../../services/sharingSessionApi.ts";
import DeleteDocumentDialog from "../session-document-delete-dialog/DeleteDocumentDialog.tsx";


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
    const token = useToken();
    const [isDeleteDialogOpen, setIsDeleteDialogOpen] = React.useState(false);

    const handleDownload = async () =>
    {
        try
        {
            const data = await downloadSharingSessionDocument(session.id, sessionDocument.id, token);
            const url = window.URL.createObjectURL(new Blob([data], {type: 'application/octet-stream'}));
            const link = window.document.createElement('a');

            link.id = 'f-download-link';
            link.href = url;
            link.setAttribute('download', `${sessionDocument.title}.pdf`); // or any other extension

            window.document.body.appendChild(link);

            link.click();

            window.document.getElementById('f-download-link')?.remove();
        }
        catch (error)
        {
            alert("Download failed");
            console.error("Error downloading document:", error);
        }
    };

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
                                  onClick={handleDownload}>Download</MenuItem>
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
            <DeleteDocumentDialog sessionDocument={sessionDocument}
                                  session={session}
                                  isOpen={isDeleteDialogOpen}
                                  onClose={() => setIsDeleteDialogOpen(false)}
                                  onDocumentDeleted={onDocumentDeleted}/>
        </>
    );
};

export default DocumentActionsMenu;