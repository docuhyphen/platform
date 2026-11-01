import {Button, Menu, MenuItem, MenuList, MenuPopover, MenuTrigger} from '@fluentui/react-components';
import {MoreVerticalRegular} from '@fluentui/react-icons';
import {DownloadIcon} from '../../../../../components/IconBundles.tsx';
import {DocumentLibraryEntrySummaryDto} from '../../../../../models/models.tsx';
import DocumentLibraryEntryCloneItems from '../document-library-entry-clone-items/DocumentLibraryEntryCloneItems.tsx';

interface DocumentLibraryEntryViewMenuProps
{
    entry: DocumentLibraryEntrySummaryDto;
    onDownload: () => void;
    onCloneToPersonal?: () => void;
    onCloneToOrg?: () => void;
}

const DocumentLibraryEntryViewMenu = ({entry, onDownload, onCloneToPersonal, onCloneToOrg}: DocumentLibraryEntryViewMenuProps) => (
    <Menu>
        <MenuTrigger disableButtonEnhancement>
            <Button id={`doc-menu-btn-${entry.id}`}
                    size="small"
                    appearance="subtle"
                    shape="circular"
                    icon={<MoreVerticalRegular/>}
                    aria-label="More actions"/>
        </MenuTrigger>
        <MenuPopover id={"document-library-entry-card-menu-popover-1"}>
            <MenuList id={"document-library-entry-card-menu-list-1"}>
                {entry.hasFile && (
                    <MenuItem id={`doc-download-item-${entry.id}`}
                              icon={<DownloadIcon/>}
                              onClick={onDownload}>
                        Download
                    </MenuItem>
                )}
                <DocumentLibraryEntryCloneItems entry={entry}
                                                onCloneToPersonal={onCloneToPersonal}
                                                onCloneToOrg={onCloneToOrg}/>
            </MenuList>
        </MenuPopover>
    </Menu>
);

export default DocumentLibraryEntryViewMenu;
