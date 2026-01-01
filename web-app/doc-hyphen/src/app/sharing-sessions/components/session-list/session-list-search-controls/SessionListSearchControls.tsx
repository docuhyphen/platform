import React from 'react';
import {
    Button,
    Divider,
    Field,
    Menu,
    MenuGroup,
    MenuGroupHeader,
    MenuItem,
    MenuList,
    MenuPopover,
    MenuTrigger,
    SearchBox,
    Tooltip
} from "@fluentui/react-components";
import {FilterIcon, SortDownIcon, SortUpIcon} from "../../../../components/IconBundles.tsx";
import {useSessionListSearchControlsStyles} from "./SessionListSearchControlsStyles.tsx";
import {ArrowSortDownRegular, ArrowSortUpRegular, CheckmarkRegular} from "@fluentui/react-icons";

interface SessionListSearchControlsProps
{
    searchQuery: string;
    onSearchQueryChange: (query: string) => void;
    onFilterChange: (status: string | null) => void;
    onInitiatorFilterChange: (initiatedBy: boolean | null) => void;
    onSortChange: (field: string, direction: string) => void;
    selectedStatus: string | null;
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
        onFilterChange,
        onInitiatorFilterChange,
        onSortChange,
        selectedStatus,
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
    }

    const onSessionNameSortChange = () =>
    {
        onSortChange("sessionName", sortBy === "sessionName" && sortDirection === "DESC" ? "ASC" : "DESC");
    }

    const onLastActivitySortChange = () =>
    {
        onSortChange("lastActivity", sortBy === "lastActivity" && sortDirection === "DESC" ? "ASC" : "DESC");
    }

    const getSortIcon = (field: string) =>
    {
        return sortBy === field ? (sortDirection === "DESC" ? <ArrowSortDownRegular/> : <ArrowSortUpRegular/>) : null;
    }

    const getSortDirectionIcon = (direction: string) =>
    {
        return (direction === "DESC" ? <SortDownIcon/> : <SortUpIcon/>)
    }

    return (
        <section className={styles.container}>
            <Field className={styles.searchField}>
                <SearchBox
                    placeholder="Search Document Sharing Sessions"
                    maxLength={50}
                    value={searchQuery}
                    onChange={(_, data) => onSearchQueryChange(data.value)}
                />
            </Field>
            <Menu>
                <MenuTrigger>
                    <Tooltip content="Filter sharing sessions"
                             relationship="description">
                        <Button icon={<FilterIcon/>}
                                appearance={selectedStatus || selectedInitiator !== null ? "primary" : "subtle"}/>
                    </Tooltip>
                </MenuTrigger>
                <MenuPopover>
                    <MenuList>
                        <MenuGroup>
                            <MenuGroupHeader>Status</MenuGroupHeader>
                            <MenuItem
                                onClick={() => onFilterChange("INITIATED")}
                                icon={selectedStatus === "INITIATED" && <CheckmarkRegular/> || null}>
                                Initiated
                            </MenuItem>
                            <MenuItem
                                onClick={() => onFilterChange("ACCEPTED_STARTED")}
                                icon={selectedStatus === "ACCEPTED_STARTED" && <CheckmarkRegular/> || null}>
                                Started
                            </MenuItem>
                            <MenuItem
                                onClick={() => onFilterChange("REJECTED")}
                                icon={selectedStatus === "REJECTED" && <CheckmarkRegular/> || null}>
                                Rejected
                            </MenuItem>
                            <MenuItem
                                onClick={() => onFilterChange("ENDED")}
                                icon={selectedStatus === "ENDED" && <CheckmarkRegular/> || null}>
                                Ended
                            </MenuItem>
                        </MenuGroup>
                        <Divider title="Participation"/>
                        <MenuGroup>
                            <MenuGroupHeader>Participation</MenuGroupHeader>
                            <MenuItem
                                onClick={() => onInitiatorFilterChange(true)}
                                icon={selectedInitiator === true && <CheckmarkRegular/> || null}>
                                Initiated by me
                            </MenuItem>
                            <MenuItem
                                onClick={() => onInitiatorFilterChange(false)}
                                icon={selectedInitiator === false && <CheckmarkRegular/> || null}>
                                Initiated by others
                            </MenuItem>
                        </MenuGroup>
                    </MenuList>
                </MenuPopover>
            </Menu>
            <Menu>
                <MenuTrigger>
                    <Tooltip content={getSortDescription(sortBy, sortDirection)}
                             relationship="description">
                        <Button icon={getSortDirectionIcon(sortDirection)}
                                appearance="subtle"/>
                    </Tooltip>
                </MenuTrigger>
                <MenuPopover>
                    <MenuList>
                        <MenuItem
                            onClick={onCreatedDateSortChange}
                            icon={getSortIcon("createdDate")}>
                            Date created
                        </MenuItem>
                        <MenuItem
                            onClick={onSessionNameSortChange}
                            icon={getSortIcon("sessionName")}>
                            Session name
                        </MenuItem>
                        <MenuItem
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