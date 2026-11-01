import {Button, Menu, MenuItem, MenuList, MenuPopover, MenuTrigger, Tooltip} from '@fluentui/react-components';
import {CheckmarkIcon, SortDownIcon, SortUpIcon} from '../../../../components/IconBundles.tsx';
import {DocumentLibrarySortOrder} from '../../documentLibraryListTypes.ts';
import {documentLibrarySortLabels, documentLibrarySortOrders} from '../../documentLibraryListFilters.ts';

interface DocumentLibrarySortMenuProps
{
    sortOrder: DocumentLibrarySortOrder;
    onSortOrderChange: (order: DocumentLibrarySortOrder) => void;
}

const DocumentLibrarySortMenu = ({sortOrder, onSortOrderChange}: DocumentLibrarySortMenuProps) => (
    <Menu>
        <MenuTrigger>
            <Tooltip content={documentLibrarySortLabels[sortOrder]}
                     relationship="description">
                <Button id="doc-sort-btn"
                        icon={sortOrder === 'nameDesc' ? <SortDownIcon/> : <SortUpIcon/>}
                        appearance={sortOrder !== 'default' ? 'primary' : 'subtle'}
                        shape="circular"/>
            </Tooltip>
        </MenuTrigger>
        <MenuPopover id={"document-library-tab-menu-popover-1"}>
            <MenuList id={"document-library-tab-menu-list-1"}>
                {documentLibrarySortOrders.map((order, index) => (
                    <MenuItem id={`document-library-tab-menu-item-${index + 1}`}
                              key={order}
                              icon={sortOrder === order ? <CheckmarkIcon/> : undefined}
                              onClick={() => onSortOrderChange(order)}>
                        {documentLibrarySortLabels[order]}
                    </MenuItem>
                ))}
            </MenuList>
        </MenuPopover>
    </Menu>
);

export default DocumentLibrarySortMenu;
