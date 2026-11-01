import {Button, Menu, MenuItem, MenuList, MenuPopover, MenuTrigger} from '@fluentui/react-components';
import {MoreVerticalRegular} from '@fluentui/react-icons';
import {
    ActivateIcon,
    CopyIcon,
    DeactivateIcon,
    DeleteIcon,
    DownloadIcon,
    EditIcon,
    PublishIcon,
    UnpublishIcon,
    UploadIcon,
} from '../../../../../components/IconBundles.tsx';
import {DocumentLibraryEntrySummaryDto} from '../../../../../models/models.tsx';
import {DocumentLibraryEntryCardCallbacks} from '../../documentLibraryEntryCardTypes.ts';
import DocumentLibraryEntryCloneItems from '../document-library-entry-clone-items/DocumentLibraryEntryCloneItems.tsx';

interface DocumentLibraryEntryManageMenuProps extends DocumentLibraryEntryCardCallbacks
{
    entry: DocumentLibraryEntrySummaryDto;
    showPublishToggle: boolean;
}

const DocumentLibraryEntryManageMenu = ({
    entry,
    showPublishToggle,
    onEdit,
    onUpload,
    onDownload,
    onPublish,
    onActivate,
    onClone,
    onDelete,
    onCloneToPersonal,
    onCloneToOrg,
}: DocumentLibraryEntryManageMenuProps) => (
    <Menu>
        <MenuTrigger disableButtonEnhancement>
            <Button id={`doc-menu-btn-${entry.id}`}
                    size="small"
                    appearance="subtle"
                    shape="circular"
                    icon={<MoreVerticalRegular/>}
                    aria-label="More actions"/>
        </MenuTrigger>
        <MenuPopover id={"document-library-entry-card-menu-popover-2"}>
            <MenuList id={"document-library-entry-card-menu-list-2"}>
                <MenuItem id={`doc-edit-item-${entry.id}`}
                          icon={<EditIcon/>}
                          onClick={onEdit}>
                    Edit
                </MenuItem>
                {entry.hasFile && (
                    <MenuItem id={`doc-download-item-${entry.id}`}
                              icon={<DownloadIcon/>}
                              onClick={onDownload}>
                        Download
                    </MenuItem>
                )}
                <MenuItem id={`doc-upload-item-${entry.id}`}
                          icon={<UploadIcon/>}
                          onClick={onUpload}>
                    {entry.hasFile ? 'Replace file' : 'Upload file'}
                </MenuItem>
                {showPublishToggle && (
                    <MenuItem id={`doc-publish-item-${entry.id}`}
                              icon={entry.isPublished ? <UnpublishIcon/> : <PublishIcon/>}
                              onClick={onPublish}>
                        {entry.isPublished ? 'Unpublish' : 'Publish'}
                    </MenuItem>
                )}
                <MenuItem id={`doc-activate-item-${entry.id}`}
                          icon={entry.isActive ? <DeactivateIcon/> : <ActivateIcon/>}
                          onClick={onActivate}>
                    {entry.isActive ? 'Deactivate' : 'Activate'}
                </MenuItem>
                {entry.scope === 'APP' ? (
                    <DocumentLibraryEntryCloneItems entry={entry}
                                                    onCloneToPersonal={onCloneToPersonal}
                                                    onCloneToOrg={onCloneToOrg}/>
                ) : (
                    <MenuItem id={`doc-clone-item-${entry.id}`}
                              icon={<CopyIcon/>}
                              onClick={onClone}>
                        Clone
                    </MenuItem>
                )}
                <MenuItem id={`doc-delete-item-${entry.id}`}
                          icon={<DeleteIcon/>}
                          onClick={onDelete}>
                    Delete
                </MenuItem>
            </MenuList>
        </MenuPopover>
    </Menu>
);

export default DocumentLibraryEntryManageMenu;
