import React from 'react';
import {Button, Divider, Menu, MenuItem, MenuList, MenuPopover, MenuTrigger} from "@fluentui/react-components";
import {
    ArrowDownloadRegular,
    ArrowUploadRegular,
    DeleteRegular,
    InfoRegular,
    MoreVerticalRegular,
    NotepadEditRegular
} from "@fluentui/react-icons";
import {DocumentDetailedDto, SharingSessionDetailedDto} from "../../models/models.tsx";
import useToken from "../../../context/useToken.tsx";
import {downloadSharingSessionDocument} from "../../../services/sharingSessionApi.ts";
import DeleteDocumentDialog from "./DeleteDocumentDialog.tsx";


interface DocumentActionsMenuProps
{
    session: SharingSessionDetailedDto;
    sessionDocument: DocumentDetailedDto;
    onUpload: () => void;
    onOpenDetailsSidebar: () => void;
    onDocumentDeleted: (documentId: string) => void;
}

const DocumentActionsMenu: React.FC<DocumentActionsMenuProps> = (
    {
        session,
        sessionDocument,
        onUpload,
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

    return (
        <>
        <Menu positioning={{autoSize: true}}>
            <MenuTrigger disableButtonEnhancement>
                <Button icon={<MoreVerticalRegular/>} appearance="subtle"/>
            </MenuTrigger>
            <MenuPopover>
                <MenuList>
                    <MenuItem icon={<NotepadEditRegular/>}>Edit</MenuItem>
                    <Divider/>
                    <MenuItem
                        icon={<ArrowUploadRegular/>}
                        onClick={onUpload}>
                        Upload
                    </MenuItem>
                    <MenuItem icon={<ArrowDownloadRegular/>}
                              onClick={handleDownload}>Download</MenuItem>
                    {/*<MenuItem icon={<DocumentPrintRegular/>}*/}
                    {/*          onClick={handlePrint}>Print</MenuItem>*/}
                    <MenuItem icon={<DeleteRegular/>} onClick={() =>
                    {
                        console.log("sessionDocument", sessionDocument)
                        setIsDeleteDialogOpen(true)
                    }}
                    >Delete</MenuItem>
                    <Divider/>
                    <MenuItem icon={<InfoRegular/>}
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