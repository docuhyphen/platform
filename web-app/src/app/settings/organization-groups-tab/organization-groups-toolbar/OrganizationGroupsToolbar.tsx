import {Button, SearchBox} from "@fluentui/react-components";
import {FilterIcon, GroupAddIcon, SortDownIcon, SortUpIcon} from "../../../components/IconBundles.tsx";
import SettingsToolbarMenu, {
    SettingsToolbarMenuOption
} from "../../components/settings-toolbar-menu/SettingsToolbarMenu.tsx";
import {useOrganizationGroupsToolbarStyles} from "./OrganizationGroupsToolbarStyles.tsx";

export type OrganizationGroupsStatusFilter = "ALL" | "ACTIVE" | "INACTIVE";
export type OrganizationGroupsSortOption =
    "NAME_ASC" | "NAME_DESC" | "MEMBERS_ASC" | "MEMBERS_DESC" | "STATUS_ASC" | "STATUS_DESC";

const statusOptions: SettingsToolbarMenuOption<OrganizationGroupsStatusFilter>[] = [
    {value: "ALL", label: "All statuses"},
    {value: "ACTIVE", label: "Active"},
    {value: "INACTIVE", label: "Inactive"}
];
const sortOptions: SettingsToolbarMenuOption<OrganizationGroupsSortOption>[] = [
    {value: "NAME_ASC", label: "Name: A to Z"},
    {value: "NAME_DESC", label: "Name: Z to A"},
    {value: "MEMBERS_DESC", label: "Members: Most first"},
    {value: "MEMBERS_ASC", label: "Members: Fewest first"},
    {value: "STATUS_ASC", label: "Status: Active first"},
    {value: "STATUS_DESC", label: "Status: Inactive first"}
];

interface OrganizationGroupsToolbarProps
{
    searchQuery: string;
    statusFilter: OrganizationGroupsStatusFilter;
    sortOption: OrganizationGroupsSortOption;
    onSearchChange: (value: string) => void;
    onStatusChange: (value: OrganizationGroupsStatusFilter) => void;
    onSortChange: (value: OrganizationGroupsSortOption) => void;
    onCreate: () => void;
}

const OrganizationGroupsToolbar = (props: OrganizationGroupsToolbarProps) =>
{
    const styles = useOrganizationGroupsToolbarStyles();
    return (
        <div
            id="organization-groups-toolbar"
            className={styles.container}
        >
            <SearchBox
                id="organization-groups-search"
                className={styles.searchBox}
                placeholder="Search by group or member"
                value={props.searchQuery}
                onChange={(_, data) => props.onSearchChange(data.value)}
            />
            <div className={styles.tools}>
                <SettingsToolbarMenu
                    id="organization-groups-status-filter"
                    icon={<FilterIcon/>}
                    tooltip="Filter by status"
                    value={props.statusFilter}
                    defaultValue="ALL"
                    options={statusOptions}
                    onChange={props.onStatusChange}
                />
                <SettingsToolbarMenu
                    id="organization-groups-sort"
                    icon={props.sortOption.endsWith("DESC") ? <SortDownIcon/> : <SortUpIcon/>}
                    tooltip="Sort groups"
                    value={props.sortOption}
                    defaultValue="NAME_ASC"
                    options={sortOptions}
                    onChange={props.onSortChange}
                />
            </div>
            <Button
                id="org-groups-create-group"
                className={styles.actionButton}
                icon={<GroupAddIcon/>}
                appearance="subtle"
                shape="circular"
                onClick={props.onCreate}
            >
                Create Group
            </Button>
        </div>
    );
};

export default OrganizationGroupsToolbar;
