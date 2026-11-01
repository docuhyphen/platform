import React from "react";
import {Combobox, ComboboxProps, Option} from "@fluentui/react-components";
import {OrganizationGroupBasicDto} from "../../../../../../services/organizationApi";
import {MAX_VISIBLE_GROUPS} from "../myOrganizationRecipientsUtils.ts";
import {useOrganizationGroupComboboxStyles} from "./OrganizationGroupComboboxStyles.tsx";

interface OrganizationGroupComboboxProps
{
    groups: OrganizationGroupBasicDto[];
    query: string;
    onQueryChange: (query: string) => void;
    onOptionSelect: ComboboxProps["onOptionSelect"];
}

const OrganizationGroupCombobox: React.FC<OrganizationGroupComboboxProps> = (
    {
        groups,
        query,
        onQueryChange,
        onOptionSelect,
    }) =>
{
    const styles = useOrganizationGroupComboboxStyles();
    const normalizedGroupQuery = query.trim().toLocaleLowerCase();
    const matchingGroups = groups
        .filter(group => !normalizedGroupQuery || group.name.toLocaleLowerCase().includes(normalizedGroupQuery))
        .sort((left, right) => left.name.localeCompare(right.name, undefined, {sensitivity: "base"}));
    const hiddenGroupCount = Math.max(0, matchingGroups.length - MAX_VISIBLE_GROUPS);
    const visibleGroups = matchingGroups.slice(0, MAX_VISIBLE_GROUPS);

    return (
        <Combobox
            id={"my-org-group-combobox"}
            listbox={{className: styles.groupListbox}}
            onOptionSelect={onOptionSelect}
            placeholder="Select Group/Team/Department"
            onChange={(ev) => onQueryChange(ev.target.value)}
            value={query}>
            {visibleGroups.map(group =>
            {
                const orgGroupCount = group.members?.length ?? 0;
                const orgGroupCountText = orgGroupCount > 1 ? "s" : "";

                return (
                    <Option id={`my-organization-recipients-option-1-${group.id}`}
                            key={group.id}
                            text={group.name}
                            value={group.id || ''}>
                        {`${group.name} (${orgGroupCount} member${orgGroupCountText})`}
                    </Option>
                );
            })}
            {hiddenGroupCount > 0 && (
                <Option
                    id={"my-organization-recipients-option-2"}
                    disabled
                    text={`${hiddenGroupCount} more groups. Type to narrow the list.`}
                >
                    {hiddenGroupCount} more groups. Type to narrow the list.
                </Option>
            )}
        </Combobox>
    );
};

export default OrganizationGroupCombobox;
