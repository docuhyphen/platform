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
import {useSessionListSearchControlsStyles} from "./SessionListSearchControlsStyles.tsx";
import {ArrowSortDownRegular, ArrowSortUpRegular, CheckmarkRegular} from "@fluentui/react-icons";

interface SessionListSearchControlsProps
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

const SessionListSearchControls: React.FC<SessionListSearchControlsProps> = (
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
    const styles = useSessionListSearchControlsStyles();

    const getSortDescription = (field: string, direction: string): string =>
    {
        switch (field)
        {
            case "createdDate":
                return direction === "DESC" ? "Created Date (Newest First)" : "Created Date (Oldest First)";
            case "sessionName":
                return direction === "DESC" ? "Session Name (Z-A)" : "Session Name (A-Z)";
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

    const onSessionNameSortChange = () =>
    {
        onSortChange("sessionName", sortBy === "sessionName" && sortDirection === "DESC" ? "ASC" : "DESC");
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
                    id="session-list-search-input"
                    placeholder="Search sessions"
                    maxLength={50}
                    value={searchQuery}
                    onChange={(_, data) => onSearchQueryChange(data.value)}
                />
            </Field>
            <Menu>
                <MenuTrigger>
                    <Tooltip content="Filter sessions"
                             relationship="description">
                        <Button icon={<FilterIcon/>}
                                id="session-list-filter-menu-trigger"
                                appearance={selectedInitiator !== null ? "primary" : "subtle"}/>
                    </Tooltip>
                </MenuTrigger>
                <MenuPopover>
                    <MenuList id="session-list-filter-menu-list">
                        <MenuGroup>
                            <MenuGroupHeader>Participation</MenuGroupHeader>
                            <MenuItem
                                id="session-list-filter-initiated-by-me"
                                onClick={() => onInitiatorFilterChange(true)}
                                icon={selectedInitiator === true && <CheckmarkRegular/> || null}>
                                Initiated by me
                            </MenuItem>
                            <MenuItem
                                id="session-list-filter-initiated-by-others"
                                onClick={() => onInitiatorFilterChange(false)}
                                icon={selectedInitiator === false && <CheckmarkRegular/> || null}>
                                Initiated by others
                            </MenuItem>
                            {selectedInitiator !== null && (
                                <MenuItem id="session-list-filter-clear" onClick={() => onInitiatorFilterChange(null)}>
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
                                id="session-list-sort-menu-trigger"
                                appearance="subtle"/>
                    </Tooltip>
                </MenuTrigger>
                <MenuPopover>
                    <MenuList id="session-list-sort-menu-list">
                        <MenuItem
                            id="session-list-sort-created-date"
                            onClick={onCreatedDateSortChange}
                            icon={getSortIcon("createdDate")}>
                            Date created
                        </MenuItem>
                        <MenuItem
                            id="session-list-sort-session-name"
                            onClick={onSessionNameSortChange}
                            icon={getSortIcon("sessionName")}>
                            Session name
                        </MenuItem>
                        <MenuItem
                            id="session-list-sort-last-activity"
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

export default SessionListSearchControls;
