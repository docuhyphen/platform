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
    SearchBox,
    Field,
    Tooltip
} from "@fluentui/react-components";
import {FilterIcon, SortDownIcon, SortUpIcon} from "../../../../components/IconBundles.tsx";
import {useExchangeListSearchControlsStyles} from "./ExchangeListSearchControlsStyles.tsx";
import {ArrowSortDownRegular, ArrowSortUpRegular, CheckmarkRegular} from "@fluentui/react-icons";

interface ExchangeListSearchControlsProps
{
    searchQuery: string;
    onSearchQueryChange: (query: string) => void;
    onInitiatorFilterChange: (initiatedBy: boolean | null) => void;
    onSortChange: (field: string, direction: string) => void;
    selectedInitiator: boolean | null;
    sortBy: string;
    sortDirection: string;
    onClearFilters: () => void;
    hasActiveFilters: boolean;
}

const ExchangeListSearchControls: React.FC<ExchangeListSearchControlsProps> = (
    {
        searchQuery,
        onSearchQueryChange,
        onInitiatorFilterChange,
        onSortChange,
        selectedInitiator,
        sortBy,
        sortDirection,
        onClearFilters,
        hasActiveFilters
    }) =>
{
    const styles = useExchangeListSearchControlsStyles();

    const getSortDescription = (field: string, direction: string): string =>
    {
        switch (field)
        {
            case "createdDate":
                return direction === "DESC" ? "Created Date (Newest First)" : "Created Date (Oldest First)";
            case "name":
                return direction === "DESC" ? "Exchange Name (Z-A)" : "Exchange Name (A-Z)";
            case "lastActivity":
                return direction === "DESC" ? "Last Activity (Recent First)" : "Last Activity (Oldest First)";
            default:
                return `${field} (${direction})`;
        }
    };

    const onCreatedDateSortChange = () =>
    {
        onSortChange("createdDate", sortBy === "createdDate" && sortDirection === "DESC" ? "ASC" : "DESC");
    };

    const onExchangeNameSortChange = () =>
    {
        onSortChange("name", sortBy === "name" && sortDirection === "DESC" ? "ASC" : "DESC");
    };

    const onLastActivitySortChange = () =>
    {
        onSortChange("lastActivity", sortBy === "lastActivity" && sortDirection === "DESC" ? "ASC" : "DESC");
    };

    const getSortIcon = (field: string) =>
    {
        return sortBy === field ? (sortDirection === "DESC" ? <ArrowSortDownRegular/> : <ArrowSortUpRegular/>) : null;
    };

    const getSortDirectionIcon = (direction: string) =>
    {
        return (direction === "DESC" ? <SortDownIcon/> : <SortUpIcon/>);
    };

    return (
        <section className={styles.container}>
            <Field className={styles.searchField}>
                <SearchBox
                    id="exchange-list-search-input"
                    placeholder="Search exchanges"
                    maxLength={50}
                    value={searchQuery}
                    onChange={(_, data) => onSearchQueryChange(data.value)}
                />
            </Field>
            <Menu>
                <MenuTrigger>
                    <Tooltip content="Filter exchanges"
                             relationship="description">
                        <Button icon={<FilterIcon/>}
                                id="exchange-list-filter-menu-trigger"
                                appearance={selectedInitiator !== null ? "primary" : "subtle"}/>
                    </Tooltip>
                </MenuTrigger>
                <MenuPopover>
                    <MenuList id="exchange-list-filter-menu-list">
                        <MenuGroup>
                            <MenuGroupHeader>Participation</MenuGroupHeader>
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
                                <MenuItem id="exchange-list-filter-clear" onClick={() => onInitiatorFilterChange(null)}>
                                    Clear
                                </MenuItem>
                            )}
                        </MenuGroup>
                    </MenuList>
                </MenuPopover>
            </Menu>
            <Menu>
                <MenuTrigger>
                    <Tooltip content={getSortDescription(sortBy, sortDirection)}
                             relationship="description">
                        <Button icon={getSortDirectionIcon(sortDirection)}
                                id="exchange-list-sort-menu-trigger"
                                appearance="subtle"/>
                    </Tooltip>
                </MenuTrigger>
                <MenuPopover>
                    <MenuList id="exchange-list-sort-menu-list">
                        <MenuItem
                            id="exchange-list-sort-created-date"
                            onClick={onCreatedDateSortChange}
                            icon={getSortIcon("createdDate")}>
                            Date created
                        </MenuItem>
                        <MenuItem
                            id="exchange-list-sort-exchange-name"
                            onClick={onExchangeNameSortChange}
                            icon={getSortIcon("name")}>
                            Exchange name
                        </MenuItem>
                        <MenuItem
                            id="exchange-list-sort-last-activity"
                            onClick={onLastActivitySortChange}
                            icon={getSortIcon("lastActivity")}>
                            Last activity
                        </MenuItem>
                    </MenuList>
                </MenuPopover>
            </Menu>
        </section>
    );
};

export default ExchangeListSearchControls;
