import React from 'react';
import {
    Button,
    Menu,
    MenuItem,
    MenuList,
    MenuPopover,
    MenuTrigger,
    Tooltip,
} from '@fluentui/react-components';
import {CheckmarkIcon, SortDownIcon, SortUpIcon} from "../../../../components/IconBundles.tsx";
import {SortOrder, sortOrderLabel} from '../blueprintPickerTypes.ts';

interface BlueprintPickerSortMenuProps
{
    sortOrder: SortOrder;
    onSortOrderChange: (order: SortOrder) => void;
}

const BlueprintPickerSortMenu: React.FC<BlueprintPickerSortMenuProps> = ({sortOrder, onSortOrderChange}) =>
{
    return (
        <Menu>
            <MenuTrigger>
                <Tooltip
                    content={sortOrderLabel[sortOrder]}
                    relationship="description"
                >
                    <Button
                        id="blueprint-picker-sort-btn"
                        icon={sortOrder === 'nameDesc' ? <SortDownIcon/> : <SortUpIcon/>}
                        appearance={sortOrder !== 'default' ? 'primary' : 'subtle'}
                        shape="circular"
                    />
                </Tooltip>
            </MenuTrigger>
            <MenuPopover id={"blueprint-picker-search-row-menu-popover"}>
                <MenuList id={"blueprint-picker-search-row-menu-list"}>
                    <MenuItem
                        id={"blueprint-picker-recently-updated-menu-item"}
                        icon={sortOrder === 'default' ? <CheckmarkIcon/> : undefined}
                        onClick={() => onSortOrderChange('default')}
                    >
                        {sortOrderLabel.default}
                    </MenuItem>
                    <MenuItem
                        id={"blueprint-picker-name-a-z-menu-item"}
                        icon={sortOrder === 'nameAsc' ? <CheckmarkIcon/> : undefined}
                        onClick={() => onSortOrderChange('nameAsc')}
                    >
                        {sortOrderLabel.nameAsc}
                    </MenuItem>
                    <MenuItem
                        id={"blueprint-picker-name-z-a-menu-item"}
                        icon={sortOrder === 'nameDesc' ? <CheckmarkIcon/> : undefined}
                        onClick={() => onSortOrderChange('nameDesc')}
                    >
                        {sortOrderLabel.nameDesc}
                    </MenuItem>
                </MenuList>
            </MenuPopover>
        </Menu>
    );
};

export default BlueprintPickerSortMenu;
