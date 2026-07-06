import {Button, SearchBox} from "@fluentui/react-components";
import {FilterIcon, PersonAddIcon, SortDownIcon, SortUpIcon} from "../../../components/IconBundles.tsx";
import {OrganizationRoleName} from "../../../../services/types/roles.ts";
import SettingsToolbarMenu, {
    SettingsToolbarMenuOption
} from "../../components/settings-toolbar-menu/SettingsToolbarMenu.tsx";
import OrganizationPeopleFilterPopover from "./OrganizationPeopleFilterPopover.tsx";
import {useOrganizationPeopleToolbarStyles} from "./OrganizationPeopleToolbarStyles.tsx";

export type OrganizationPeopleStatus = "ACTIVE" | "INACTIVE";
export type OrganizationPeopleSortOption =
    "NAME_ASC" | "NAME_DESC" | "EMAIL_ASC" | "EMAIL_DESC" | "STATUS_ASC" | "STATUS_DESC";

const sortOptions: SettingsToolbarMenuOption<OrganizationPeopleSortOption>[] = [
    {value: "NAME_ASC", label: "Name: A to Z"},
    {value: "NAME_DESC", label: "Name: Z to A"},
    {value: "EMAIL_ASC", label: "Email: A to Z"},
    {value: "EMAIL_DESC", label: "Email: Z to A"},
    {value: "STATUS_ASC", label: "Status: Active first"},
    {value: "STATUS_DESC", label: "Status: Inactive first"}
];

interface OrganizationPeopleToolbarProps
{
    searchQuery: string;
    statusFilters: Set<OrganizationPeopleStatus>;
    roleFilters: Set<OrganizationRoleName>;
    sortOption: OrganizationPeopleSortOption;
    onSearchChange: (value: string) => void;
    onStatusToggle: (value: OrganizationPeopleStatus) => void;
    onRoleToggle: (value: OrganizationRoleName) => void;
    onSortChange: (value: OrganizationPeopleSortOption) => void;
    onAdd: () => void;
}

const OrganizationPeopleToolbar = (props: OrganizationPeopleToolbarProps) =>
{
    const styles = useOrganizationPeopleToolbarStyles();
    return (
        <div
            id="organization-people-toolbar"
            className={styles.container}
        >
            <SearchBox
                id="organization-people-search"
                className={styles.searchBox}
                placeholder="Search by name or email"
                value={props.searchQuery}
                onChange={(_, data) => props.onSearchChange(data.value)}
            />
            <div className={styles.tools}>
                <OrganizationPeopleFilterPopover
                    icon={<FilterIcon/>}
                    statusFilters={props.statusFilters}
                    roleFilters={props.roleFilters}
                    onStatusToggle={props.onStatusToggle}
                    onRoleToggle={props.onRoleToggle}
                />
                <SettingsToolbarMenu
                    id="organization-people-sort"
                    icon={props.sortOption.endsWith("DESC") ? <SortDownIcon/> : <SortUpIcon/>}
                    tooltip="Sort people"
                    value={props.sortOption}
                    defaultValue="NAME_ASC"
                    options={sortOptions}
                    onChange={props.onSortChange}
                />
            </div>
            <Button
                id="org-people-add-person-btn"
                className={styles.actionButton}
                icon={<PersonAddIcon/>}
                appearance="subtle"
                shape="circular"
                onClick={props.onAdd}
            >
                Add Person
            </Button>
        </div>
    );
};

export default OrganizationPeopleToolbar;
