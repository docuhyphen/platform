import {ReactElement} from "react";
import {
    Button,
    Checkbox,
    Popover,
    PopoverSurface,
    PopoverTrigger,
    Text,
    Tooltip
} from "@fluentui/react-components";
import {OrganizationRoleDisplayNames, OrganizationRoleName} from "../../../../services/types/roles.ts";
import type {OrganizationPeopleStatus} from "./OrganizationPeopleToolbar.tsx";
import {useOrganizationPeopleToolbarStyles} from "./OrganizationPeopleToolbarStyles.tsx";

interface OrganizationPeopleFilterPopoverProps
{
    icon: ReactElement;
    statusFilters: Set<OrganizationPeopleStatus>;
    roleFilters: Set<OrganizationRoleName>;
    onStatusToggle: (value: OrganizationPeopleStatus) => void;
    onRoleToggle: (value: OrganizationRoleName) => void;
}

const OrganizationPeopleFilterPopover = ({
    icon,
    statusFilters,
    roleFilters,
    onStatusToggle,
    onRoleToggle
}: OrganizationPeopleFilterPopoverProps) =>
{
    const styles = useOrganizationPeopleToolbarStyles();
    const filtersActive = statusFilters.size > 0 || roleFilters.size > 0;

    return (
        <Popover positioning="below-end">
            <PopoverTrigger disableButtonEnhancement>
                <Tooltip
                    content="Filter people"
                    relationship="description"
                >
                    <Button
                        id="organization-people-filter"
                        icon={icon}
                        appearance={filtersActive ? "primary" : "subtle"}
                        shape="circular"
                    />
                </Tooltip>
            </PopoverTrigger>
            <PopoverSurface id={"organization-people-filter-popover-popover-surface-1"}
                            className={styles.filterPopover}>
                <div id={"organization-people-filter-popover-div-1"}
                     className={styles.filterSection}>
                    <Text id={"organization-people-filter-popover-text-1"}
                          className={styles.filterSectionTitle}>Status</Text>
                    <div id={"organization-people-filter-popover-div-2"}
                         className={styles.filterSectionList}>
                        <Checkbox
                            id="organization-people-filter-status-active"
                            label="Active"
                            checked={statusFilters.has("ACTIVE")}
                            onChange={() => onStatusToggle("ACTIVE")}
                        />
                        <Checkbox
                            id="organization-people-filter-status-inactive"
                            label="Inactive"
                            checked={statusFilters.has("INACTIVE")}
                            onChange={() => onStatusToggle("INACTIVE")}
                        />
                    </div>
                </div>
                <hr id={"organization-people-filter-popover-hr-1"}
                    className={styles.filterDivider}/>
                <div id={"organization-people-filter-popover-div-3"}
                     className={styles.filterSection}>
                    <Text id={"organization-people-filter-popover-text-2"}
                          className={styles.filterSectionTitle}>Role</Text>
                    <div id={"organization-people-filter-popover-div-4"}
                         className={styles.filterSectionList}>
                        {Object.values(OrganizationRoleName).map(role => (
                            <Checkbox
                                id={`organization-people-filter-role-${role.toLocaleLowerCase()}`}
                                key={role}
                                label={OrganizationRoleDisplayNames[role]}
                                checked={roleFilters.has(role)}
                                onChange={() => onRoleToggle(role)}
                            />
                        ))}
                    </div>
                </div>
            </PopoverSurface>
        </Popover>
    );
};

export default OrganizationPeopleFilterPopover;
