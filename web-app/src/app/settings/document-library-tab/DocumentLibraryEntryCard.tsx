import React from 'react';
import {
    Badge,
    Button,
    Menu,
    MenuItem,
    MenuList,
    MenuPopover,
    MenuTrigger,
    Text,
} from '@fluentui/react-components';
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
} from '../../components/IconBundles.tsx';
import {useDocumentsTabStyles} from './DocumentLibraryTabStyles.tsx';
import {DocumentLibraryEntrySummaryDto} from '../../models/models.tsx';

interface Props
{
    entry: DocumentLibraryEntrySummaryDto;
    canManage: boolean;
    showPublishToggle: boolean;
    onEdit: () => void;
    onUpload: () => void;
    onDownload: () => void;
    onPublish: () => void;
    onActivate: () => void;
    onClone: () => void;
    onDelete: () => void;
    onCloneToPersonal?: () => void;
    onCloneToOrg?: () => void;
}

const DocumentLibraryEntryCard = ({
    entry,
    canManage,
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
}: Props) =>
{
    const styles = useDocumentsTabStyles();

    return (
        <div
            id={`doc-lib-card-${entry.id}`}
            className={styles.card}
        >
            <div className={styles.cardBody}>
                <Text
                    weight="semibold"
                    size={400}
                >
                    {entry.title}
                </Text>
                {entry.description && (
                    <Text
                        size={200}
                        className={styles.descriptionText}
                    >
                        {entry.description}
                    </Text>
                )}
                <div className={styles.badgeRow}>
                    {entry.documentType && (
                        <Badge
                            id={`doc-type-badge-${entry.id}`}
                            appearance="tint"
                            color="informative"
                            size="small"
                        >
                            {entry.documentType}
                        </Badge>
                    )}
                    {!entry.hasFile && (
                        <Badge
                            id={`doc-no-file-badge-${entry.id}`}
                            appearance="tint"
                            color="warning"
                            size="small"
                        >
                            No file
                        </Badge>
                    )}
                    {showPublishToggle && (
                        <Badge
                            id={`doc-published-badge-${entry.id}`}
                            appearance="tint"
                            color={entry.isPublished ? 'success' : 'warning'}
                            size="small"
                        >
                            {entry.isPublished ? 'Published' : 'Draft'}
                        </Badge>
                    )}
                    <Badge
                        id={`doc-active-badge-${entry.id}`}
                        appearance="tint"
                        color={entry.isActive ? 'success' : 'warning'}
                        size="small"
                    >
                        {entry.isActive ? 'Active' : 'Inactive'}
                    </Badge>
                    {entry.generalTags.map(tag => (
                        <Badge
                            key={tag}
                            appearance="tint"
                            size="small"
                        >
                            {tag}
                        </Badge>
                    ))}
                </div>
            </div>
            <div className={styles.cardActions}>
                {entry.hasFile && (
                    <Button
                        id={`doc-download-btn-${entry.id}`}
                        size="small"
                        appearance="subtle"
                        shape="circular"
                        icon={<DownloadIcon/>}
                        aria-label="Download"
                        onClick={onDownload}
                    />
                )}
                {!canManage && (onCloneToPersonal || onCloneToOrg) && (
                    <Menu>
                        <MenuTrigger disableButtonEnhancement>
                            <Button
                                id={`doc-menu-btn-${entry.id}`}
                                size="small"
                                appearance="subtle"
                                shape="circular"
                                icon={<MoreVerticalRegular/>}
                                aria-label="More actions"
                            />
                        </MenuTrigger>
                        <MenuPopover>
                            <MenuList>
                                {onCloneToPersonal && (
                                    <MenuItem
                                        id={`doc-clone-personal-item-${entry.id}`}
                                        icon={<CopyIcon/>}
                                        onClick={onCloneToPersonal}
                                    >
                                        Clone to My Collection
                                    </MenuItem>
                                )}
                                {onCloneToOrg && (
                                    <MenuItem
                                        id={`doc-clone-org-item-${entry.id}`}
                                        icon={<CopyIcon/>}
                                        onClick={onCloneToOrg}
                                    >
                                        Clone to Organization
                                    </MenuItem>
                                )}
                            </MenuList>
                        </MenuPopover>
                    </Menu>
                )}
                {canManage && (
                    <Menu>
                        <MenuTrigger disableButtonEnhancement>
                            <Button
                                id={`doc-menu-btn-${entry.id}`}
                                size="small"
                                appearance="subtle"
                                shape="circular"
                                icon={<MoreVerticalRegular/>}
                                aria-label="More actions"
                            />
                        </MenuTrigger>
                        <MenuPopover>
                            <MenuList>
                                <MenuItem
                                    id={`doc-edit-item-${entry.id}`}
                                    icon={<EditIcon/>}
                                    onClick={onEdit}
                                >
                                    Edit
                                </MenuItem>
                                <MenuItem
                                    id={`doc-upload-item-${entry.id}`}
                                    icon={<UploadIcon/>}
                                    onClick={onUpload}
                                >
                                    {entry.hasFile ? 'Replace file' : 'Upload file'}
                                </MenuItem>
                                {showPublishToggle && (
                                    <MenuItem
                                        id={`doc-publish-item-${entry.id}`}
                                        icon={entry.isPublished ? <UnpublishIcon/> : <PublishIcon/>}
                                        onClick={onPublish}
                                    >
                                        {entry.isPublished ? 'Unpublish' : 'Publish'}
                                    </MenuItem>
                                )}
                                <MenuItem
                                    id={`doc-activate-item-${entry.id}`}
                                    icon={entry.isActive ? <DeactivateIcon/> : <ActivateIcon/>}
                                    onClick={onActivate}
                                >
                                    {entry.isActive ? 'Deactivate' : 'Activate'}
                                </MenuItem>
                                {entry.scope === 'APP' ? (
                                    <>
                                        {onCloneToPersonal && (
                                            <MenuItem
                                                id={`doc-clone-personal-item-${entry.id}`}
                                                icon={<CopyIcon/>}
                                                onClick={onCloneToPersonal}
                                            >
                                                Clone to My Collection
                                            </MenuItem>
                                        )}
                                        {onCloneToOrg && (
                                            <MenuItem
                                                id={`doc-clone-org-item-${entry.id}`}
                                                icon={<CopyIcon/>}
                                                onClick={onCloneToOrg}
                                            >
                                                Clone to Organization
                                            </MenuItem>
                                        )}
                                    </>
                                ) : (
                                    <MenuItem
                                        id={`doc-clone-item-${entry.id}`}
                                        icon={<CopyIcon/>}
                                        onClick={onClone}
                                    >
                                        Clone
                                    </MenuItem>
                                )}
                                <MenuItem
                                    id={`doc-delete-item-${entry.id}`}
                                    icon={<DeleteIcon/>}
                                    onClick={onDelete}
                                >
                                    Delete
                                </MenuItem>
                            </MenuList>
                        </MenuPopover>
                    </Menu>
                )}
            </div>
        </div>
    );
};

export default DocumentLibraryEntryCard;
