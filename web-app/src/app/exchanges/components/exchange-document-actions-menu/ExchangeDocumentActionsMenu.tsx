import React from 'react';
import {Button, Divider, Menu, MenuItem, MenuList, MenuPopover, MenuTrigger} from "@fluentui/react-components";
import {MoreVerticalRegular} from "@fluentui/react-icons";
import {DocumentDetailedDto, ExchangeDetailedDto, ExchangeStatus} from "../../../models/models.tsx";
import ExchangeDocumentDeleteDialog from "../exchange-document-delete-dialog/ExchangeDocumentDeleteDialog.tsx";
import ExchangeDocumentDownloadDialog from "../exchange-document-download-dialog/ExchangeDocumentDownloadDialog.tsx";
import {
    DeleteIcon,
    DocumentPreviewIcon,
    DownloadIcon,
    EditIcon,
    MoreInfoIcon,
    UploadIcon
} from "../../../components/IconBundles.tsx";
import {ExchangePermissions} from "../../ExchangePermissions.ts";

interface DocumentActionsMenuProps
{
    exchange: ExchangeDetailedDto;
    exchangeDocument: DocumentDetailedDto;
    permissions?: ExchangePermissions;
    onUpload: () => void;
    onUpdate: () => void;
    onOpenDetailsSidebar: () => void;
    onDocumentDeleted: (documentId: string) => void;
    onPreviewDocument: () => void;
}

const ExchangeDocumentActionsMenu: React.FC<DocumentActionsMenuProps> = (
    {
        exchange,
        exchangeDocument,
        permissions,
        onUpload,
        onUpdate,
        onOpenDetailsSidebar,
        onPreviewDocument,
        onDocumentDeleted
    }) =>
{
    const [isDeleteDialogOpen, setIsDeleteDialogOpen] = React.useState(false);
    const [isDownloadDocumentOpen, setIsDownloadDocumentOpen] = React.useState(false);

    const isActiveExchange =
        exchange?.status === ExchangeStatus.INITIATED ||
        exchange?.status === ExchangeStatus.ACCEPTED_STARTED;

    const canUpload = isActiveExchange && !!permissions?.canUploadDocument;
    const canEdit = isActiveExchange && !!permissions?.canEditExchangeDocument;
    const canDelete = isActiveExchange && !!permissions?.canDeleteExchangeDocument;
    const canDownload = !!permissions?.canDownloadDocumentsZip;

    const stopCardClickPropagation = (event: React.MouseEvent<HTMLElement>) =>
    {
        event.stopPropagation();
    };

    return (
        <section className={"document-actions-menu"} onClick={stopCardClickPropagation}>
            <Menu positioning={{autoSize: true}}>
                <MenuTrigger disableButtonEnhancement>
                    <Button id={`exchange-document-actions-trigger-${exchangeDocument.id}`}
                            icon={<MoreVerticalRegular/>}
                            appearance="subtle"
                            shape={"circular"}
                            onClick={stopCardClickPropagation}/>
                </MenuTrigger>
                <MenuPopover>
                    <MenuList id={`exchange-document-actions-menu-${exchangeDocument.id}`}>
                        <MenuItem icon={<EditIcon/>}
                                  id={`exchange-document-action-edit-${exchangeDocument.id}`}
                                  disabled={!canEdit}
                                  onClick={onUpdate}>
                            Edit
                        </MenuItem>
                        <Divider/>
                        <MenuItem
                            id={`exchange-document-action-upload-${exchangeDocument.id}`}
                            icon={<UploadIcon/>}
                            disabled={!canUpload}
                            onClick={onUpload}>
                            {exchangeDocument.uploadDate ? 'Re upload' : 'Upload'}
                        </MenuItem>
                        <MenuItem icon={<DownloadIcon/>}
                                  id={`exchange-document-action-download-${exchangeDocument.id}`}
                                  disabled={!canDownload}
                                  onClick={() => setIsDownloadDocumentOpen(true)}>
                            Download
                        </MenuItem>
                        <MenuItem icon={<DeleteIcon/>}
                                  id={`exchange-document-action-delete-${exchangeDocument.id}`}
                                  disabled={!canDelete}
                                  onClick={() => setIsDeleteDialogOpen(true)}>
                            Delete
                        </MenuItem>
                        <Divider/>
                        <MenuItem icon={<DocumentPreviewIcon/>}
                                  id={`exchange-document-action-preview-${exchangeDocument.id}`}
                                  disabled={!exchangeDocument.uploadDate}
                                  onClick={() => onPreviewDocument()}>
                            Preview
                        </MenuItem>
                        {isActiveExchange &&
                            <MenuItem icon={<MoreInfoIcon/>}
                                      id={`exchange-document-action-details-${exchangeDocument.id}`}
                                      onClick={() => onOpenDetailsSidebar()}>
                                Details
                            </MenuItem>
                        }
                    </MenuList>
                </MenuPopover>
            </Menu>

            <ExchangeDocumentDeleteDialog exchangeDocument={exchangeDocument}
                                         exchange={exchange}
                                         isOpen={isDeleteDialogOpen}
                                         onClose={() => setIsDeleteDialogOpen(false)}
                                         onDocumentDeleted={onDocumentDeleted}/>

            <ExchangeDocumentDownloadDialog exchangeDocument={exchangeDocument}
                                           exchange={exchange}
                                           isOpen={isDownloadDocumentOpen}
                                           onDismiss={() => setIsDownloadDocumentOpen(false)}/>
        </section>
    );
};

export default ExchangeDocumentActionsMenu;