import {Button, Menu, MenuItem, MenuList, MenuPopover, MenuTrigger, Tooltip} from '@fluentui/react-components';
import {CheckmarkIcon, SortDownIcon, SortUpIcon} from '../../../../components/IconBundles.tsx';
import {BlueprintSortOrder} from '../../blueprintListTypes.ts';
import {blueprintSortOrderLabels} from '../../blueprintListFilters.ts';

interface BlueprintsSortMenuProps
{
    sortOrder: BlueprintSortOrder;
    onSortOrderChange: (order: BlueprintSortOrder) => void;
}

const sortMenuItems: {id: string; order: BlueprintSortOrder}[] = [
    {id: 'blueprints-tab-menu-item-1', order: 'default'},
    {id: 'blueprints-tab-menu-item-2', order: 'nameAsc'},
    {id: 'blueprints-tab-menu-item-3', order: 'nameDesc'},
];

const BlueprintsSortMenu = ({sortOrder, onSortOrderChange}: BlueprintsSortMenuProps) => (
    <Menu>
        <MenuTrigger>
            <Tooltip
                content={blueprintSortOrderLabels[sortOrder]}
                relationship="description"
            >
                <Button
                    id="blueprint-sort-btn"
                    icon={sortOrder === 'nameDesc' ? <SortDownIcon/> : <SortUpIcon/>}
                    appearance={sortOrder !== 'default' ? 'primary' : 'subtle'}
                    shape="circular"
                />
            </Tooltip>
        </MenuTrigger>
        <MenuPopover id={"blueprints-tab-menu-popover-1"}>
            <MenuList id={"blueprints-tab-menu-list-1"}>
                {sortMenuItems.map(item => (
                    <MenuItem
                        id={item.id}
                        key={item.order}
                        icon={sortOrder === item.order ? <CheckmarkIcon/> : undefined}
                        onClick={() => onSortOrderChange(item.order)}
                    >
                        {blueprintSortOrderLabels[item.order]}
                    </MenuItem>
                ))}
            </MenuList>
        </MenuPopover>
    </Menu>
);

export default BlueprintsSortMenu;
