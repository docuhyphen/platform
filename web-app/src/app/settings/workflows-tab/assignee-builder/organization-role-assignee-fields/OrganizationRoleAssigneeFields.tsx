import {Select} from "@fluentui/react-components";
import {AssigneeSpecDraft} from "../../../../models/models.tsx";
import {AssigneeOption, ORGANIZATION_ROLES} from "../assigneeOptions.ts";
import {useOrganizationRoleAssigneeFieldsStyles} from "./OrganizationRoleAssigneeFieldsStyles.tsx";

interface OrganizationRoleAssigneeFieldsProps
{
    index: number;
    assignee: AssigneeSpecDraft;
    organizationRefOptions: AssigneeOption[];
    onUpdate: (patch: Partial<AssigneeSpecDraft>) => void;
}

const OrganizationRoleAssigneeFields = ({index, assignee, organizationRefOptions, onUpdate}: OrganizationRoleAssigneeFieldsProps) =>
{
    const styles = useOrganizationRoleAssigneeFieldsStyles();

    return (
        <>
            <Select id={`assignee-organization-role-name-select-${index}`}
                    className={styles.fieldInput}
                    value={assignee.roleName ?? ""}
                    onChange={(_, d) => onUpdate({roleName: d.value})}
                    size="small">
                <option id={"assignee-builder-option-7"}
                        value="">Select organization role...</option>
                {ORGANIZATION_ROLES.map(role => (
                    <option id={`assignee-builder-option-8-${role.value}`}
                            key={role.value}
                            value={role.value}>
                        {role.label}
                    </option>
                ))}
            </Select>
            <Select id={`assignee-organization-id-ref-select-${index}`}
                    className={styles.fieldInput}
                    value={assignee.organizationIdRef ?? "$subject.orgId"}
                    onChange={(_, d) => onUpdate({organizationIdRef: d.value})}
                    size="small">
                {organizationRefOptions.map(reference => (
                    <option id={`assignee-builder-option-9-${reference.value}`}
                            key={reference.value}
                            value={reference.value}>
                        {reference.label}
                    </option>
                ))}
            </Select>
        </>
    );
};

export default OrganizationRoleAssigneeFields;
