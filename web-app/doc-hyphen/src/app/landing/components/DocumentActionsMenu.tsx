import React from 'react';
import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    DialogTrigger,
    Divider,
    Menu,
    MenuItem,
    MenuList,
    MenuPopover,
    MenuTrigger,
    Spinner
} from "@fluentui/react-components";
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
import {deleteSharingSessionDocument, downloadSharingSessionDocument} from "../../../services/sharingSessionApi.ts";
import {useGlobalStyles} from "../../../GlobalStyles.tsx";


interface DocumentActionsMenuProps
{
    session: SharingSessionDetailedDto;
    sessionDocument: DocumentDetailedDto;
    onUpload: () => void;
    onOpenDetailsSidebar: () => void;
    onDocumentDeleted: (documentId: string) => void;
}

interface DeleteDocumentDialogProps
{
    isOpen: boolean;
    onClose: () => void;
    sessionDocument: DocumentDetailedDto;
    session: SharingSessionDetailedDto;
    onDocumentDeleted: (documentId: string) => void;
}

const DeleteDocumentDialog: React.FC<DeleteDocumentDialogProps> = (
    {
        isOpen,
        onClose,
        sessionDocument,
        session,
        onDocumentDeleted
    }) =>
{

    const token = useToken();
    const [deletingDocument, setDeletingDocument] = React.useState(false);
    const globalStyles = useGlobalStyles()

    const onDelete = async () =>
    {
        setDeletingDocument(true)

        try
        {
            await deleteSharingSessionDocument(session.id, sessionDocument.id, token);
            onDocumentDeleted(sessionDocument.id);
        }
        catch (error)
        {
            alert("Error deleting document");
            console.error("Error deleting document:", error);
        }
        finally
        {
            setDeletingDocument(false);
            onClose();
        }
    }

    return <>
        {<Dialog modalType="alert" open={isOpen}>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle>Deleting {sessionDocument && sessionDocument.title}</DialogTitle>
                    <DialogContent>
                        Are you sure you want to delete this document?
                    </DialogContent>
                    <DialogActions>
                        <Button appearance="primary"
                                className={globalStyles.buttonWithLoading}
                                onClick={onDelete}>
                            {deletingDocument && <Spinner size={"extra-small"}/>}
                            Delete
                        </Button>
                        <DialogTrigger disableButtonEnhancement>
                            <Button appearance="secondary"
                                    onClick={onClose}>
                                Close
                            </Button>
                        </DialogTrigger>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
        }
    </>
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

            alert("Download successful");
        }
        catch (error)
        {
            alert("Download failed");
            console.error("Error downloading document:", error);
        }
    };

    const handlePrint = () =>
    {
        window.print();
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