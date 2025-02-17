import React from 'react';
import {Button, Divider, Menu, MenuItem, MenuList, MenuPopover, MenuTrigger} from "@fluentui/react-components";
import {
    ArrowDownloadRegular,
    ArrowUploadRegular,
    DeleteRegular,
    DocumentPrintRegular,
    InfoRegular,
    MoreVerticalRegular,
    NotepadEditRegular
} from "@fluentui/react-icons";
import {DocumentDetailedDto, SharingSessionDetailedDto} from "../../models/models.tsx";
import useToken from "../../../context/useToken.tsx";
import {
    deleteSharingSessionDocument,
    downloadSharingSessionDocument,
    uploadSharingSessionDocument
} from "../../../services/sharingSessionApi.ts";


interface DocumentActionsMenuProps
{
    session: SharingSessionDetailedDto;
    document: DocumentDetailedDto;
    onUpload: () => void; // Add this line
}

const DocumentActionsMenu: React.FC<DocumentActionsMenuProps> = ({session, document, onUpload}) =>
{
    const token = useToken();

    const handleDownload = async () =>
    {
        try
        {
            const data = await downloadSharingSessionDocument(session.id, document.id, token);
            const url = window.URL.createObjectURL(new Blob([data], {type: 'application/octet-stream'}));
            const link = window.document.createElement('a');

            link.id = 'f-download-link';
            link.href = url;
            link.setAttribute('download', `${document.title}.pdf`); // or any other extension

            window.document.body.appendChild(link);

            link.click();

            window.document.getElementById('f-download-link')?.remove();

            alert("Download successful");
        }
        catch (error)
        {
            alert("Download failed");
            console.error("Error downloading document:", error);
        }
    };

    const handleDelete = async () =>
    {
        try
        {
            await deleteSharingSessionDocument(session.id, document.id, token);
            // Handle the deletion
            alert("Document deleted successfully");
        }
        catch (error)
        {
            alert("Error deleting document");
            console.error("Error deleting document:", error);
        }
    };

    const handlePrint = () =>
    {
        window.print();
    };


    return (
        <Menu positioning={{autoSize: true}}>
            <MenuTrigger disableButtonEnhancement>
                <Button icon={<MoreVerticalRegular/>} appearance="subtle"/>
            </MenuTrigger>
            <MenuPopover>
                <MenuList>
                    <MenuItem icon={<NotepadEditRegular/>}>Edit</MenuItem>
                    <Divider/>
                    <MenuItem icon={<ArrowUploadRegular/>} onClick={onUpload}>Upload</MenuItem> {/* Update this line */}
                    <MenuItem icon={<ArrowDownloadRegular/>} onClick={handleDownload}>Download</MenuItem>
                    <MenuItem icon={<DocumentPrintRegular/>} onClick={handlePrint}>Print</MenuItem>
                    <MenuItem icon={<DeleteRegular/>} onClick={handleDelete}>Delete</MenuItem>
                    <Divider/>
                    <MenuItem icon={<InfoRegular/>}>More info</MenuItem>
                </MenuList>
            </MenuPopover>
        </Menu>
    );
};

export default DocumentActionsMenu;