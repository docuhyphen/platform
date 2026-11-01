import React from 'react';
import {
    Button,
    Menu,
    MenuGroup,
    MenuGroupHeader,
    MenuItem,
    MenuList,
    MenuPopover,
    MenuTrigger,
    Tooltip
} from "@fluentui/react-components";
import {CheckmarkRegular} from "@fluentui/react-icons";
import {FilterIcon} from "../../../../../components/IconBundles.tsx";

interface ExchangeListFilterMenuProps
{
    selectedInitiator: boolean | null;
    onInitiatorFilterChange: (initiatedBy: boolean | null) => void;
}

const ExchangeListFilterMenu: React.FC<ExchangeListFilterMenuProps> = ({selectedInitiator, onInitiatorFilterChange}) =>
{
    return (
        <Menu>
            <MenuTrigger>
                <Tooltip content="Filter Exchanges"
                         relationship="description">
                    <Button
                        icon={<FilterIcon/>}
                        id="exchange-list-filter-menu-trigger"
                        appearance={selectedInitiator !== null ? "primary" : "subtle"}
                        shape={"circular"}
                    />
                </Tooltip>
            </MenuTrigger>
            <MenuPopover id={"exchange-list-search-controls-menu-popover-1"}>
                <MenuList id="exchange-list-filter-menu-list">
                    <MenuGroup id={"exchange-list-search-controls-menu-group-1"}>
                        <MenuGroupHeader id={"exchange-list-search-controls-menu-group-header-1"}>Participation</MenuGroupHeader>
                        <MenuItem
                            id="exchange-list-filter-initiated-by-me"
                            onClick={() => onInitiatorFilterChange(true)}
                            icon={selectedInitiator === true && <CheckmarkRegular/> || null}>
                            Initiated by me
                        </MenuItem>
                        <MenuItem
                            id="exchange-list-filter-initiated-by-others"
                            onClick={() => onInitiatorFilterChange(false)}
                            icon={selectedInitiator === false && <CheckmarkRegular/> || null}>
                            Initiated by others
                        </MenuItem>
                        {selectedInitiator !== null && (
                            <MenuItem id="exchange-list-filter-clear"
                                      onClick={() => onInitiatorFilterChange(null)}>
                                Clear
                            </MenuItem>
                        )}
                    </MenuGroup>
                </MenuList>
            </MenuPopover>
        </Menu>
    );
};

export default ExchangeListFilterMenu;
