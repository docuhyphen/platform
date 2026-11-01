import React from 'react';
import {Button, Menu, MenuItem, MenuList, MenuPopover, MenuTrigger, Tooltip} from "@fluentui/react-components";
import {ArrowSortDownRegular, ArrowSortUpRegular} from "@fluentui/react-icons";
import {SortDownIcon, SortUpIcon} from "../../../../../components/IconBundles.tsx";
import {EXCHANGE_LIST_SORT_OPTIONS, getNextSortDirection, getSortDescription} from "./exchangeListSortOptions.ts";

interface ExchangeListSortMenuProps
{
    sortBy: string;
    sortDirection: string;
    onSortChange: (field: string, direction: string) => void;
}

const ExchangeListSortMenu: React.FC<ExchangeListSortMenuProps> = ({sortBy, sortDirection, onSortChange}) =>
{
    const getSortIcon = (field: string) =>
    {
        return sortBy === field ? (sortDirection === "DESC" ? <ArrowSortDownRegular/> : <ArrowSortUpRegular/>) : null;
    };

    return (
        <Menu>
            <MenuTrigger>
                <Tooltip content={getSortDescription(sortBy, sortDirection)}
                         relationship="description">
                    <Button
                        icon={sortDirection === "DESC" ? <SortDownIcon/> : <SortUpIcon/>}
                        id="exchange-list-sort-menu-trigger"
                        appearance="subtle"
                        shape={"circular"}
                    />
                </Tooltip>
            </MenuTrigger>
            <MenuPopover id={"exchange-list-search-controls-menu-popover-2"}>
                <MenuList id="exchange-list-sort-menu-list">
                    {EXCHANGE_LIST_SORT_OPTIONS.map(option => (
                        <MenuItem
                            key={option.field}
                            id={option.id}
                            onClick={() => onSortChange(option.field, getNextSortDirection(option.field, sortBy, sortDirection))}
                            icon={getSortIcon(option.field)}>
                            {option.label}
                        </MenuItem>
                    ))}
                </MenuList>
            </MenuPopover>
        </Menu>
    );
};

export default ExchangeListSortMenu;
