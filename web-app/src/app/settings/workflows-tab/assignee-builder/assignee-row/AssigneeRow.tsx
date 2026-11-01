import {Button, Select} from "@fluentui/react-components";
import {AssigneeKind, AssigneeSpecDraft} from "../../../../models/models.tsx";
import {DeleteIcon} from "../../../../components/IconBundles.tsx";
import {PersonPickerItem} from "../../../../components/person-picker/personPickerTypes.ts";
import {AssigneeOption, createAssignee} from "../assigneeOptions.ts";
import AppRoleAssigneeSelect from "../app-role-assignee-select/AppRoleAssigneeSelect.tsx";
import OrganizationRoleAssigneeFields from "../organization-role-assignee-fields/OrganizationRoleAssigneeFields.tsx";
import PrincipalAssigneeField from "../principal-assignee-field/PrincipalAssigneeField.tsx";
import GroupRoleAssigneeFields from "../group-role-assignee-fields/GroupRoleAssigneeFields.tsx";
import {useAssigneeRowStyles} from "./AssigneeRowStyles.tsx";

interface AssigneeRowProps
{
    index: number;
    assignee: AssigneeSpecDraft;
    allowTenantDirectory: boolean;
    organizationRefOptions: AssigneeOption[];
    groupRefOptions: AssigneeOption[];
    people: PersonPickerItem[];
    personQuery: string;
    onPersonQueryChange: (query: string) => void;
    onUpdate: (patch: Partial<AssigneeSpecDraft>) => void;
    onReplace: (assignee: AssigneeSpecDraft) => void;
    onRemove: () => void;
}

const AssigneeRow = (props: AssigneeRowProps) =>
{
    const {
        index, assignee, allowTenantDirectory, organizationRefOptions, groupRefOptions,
        people, personQuery, onPersonQueryChange, onUpdate, onReplace, onRemove,
    } = props;
    const styles = useAssigneeRowStyles();

    return (
        <div id={`assignee-builder-div-2-${index}`}
             className={styles.row}>
            <div id={"assignee-builder-div-3"}
                 className={styles.rowFields}>

                <Select id={`assignee-kind-select-${index}`}
                        className={styles.kindSelect}
                        value={assignee.kind}
                        onChange={(_, d) => onReplace(createAssignee(d.value as AssigneeKind))}
                        size="small">
                    <option id={"assignee-builder-option-1"}
                            value="ORGANIZATION_ROLE">Organization Role</option>
                    <option id={"assignee-builder-option-2"}
                            value="APP_ROLE">App Role</option>
                    {allowTenantDirectory && <option id={"assignee-builder-option-3"}
                                                     value="PRINCIPAL">Specific User</option>}
                    {allowTenantDirectory && <option id={"assignee-builder-option-4"}
                                                     value="GROUP_ROLE">Group Members</option>}
                </Select>

                {assignee.kind === "APP_ROLE" && (
                    <AppRoleAssigneeSelect index={index}
                                           roleName={assignee.roleName}
                                           onRoleChange={roleName => onUpdate({roleName})} />
                )}

                {assignee.kind === "ORGANIZATION_ROLE" && (
                    <OrganizationRoleAssigneeFields index={index}
                                                    assignee={assignee}
                                                    organizationRefOptions={organizationRefOptions}
                                                    onUpdate={onUpdate} />
                )}

                {assignee.kind === "PRINCIPAL" && (
                    <PrincipalAssigneeField index={index}
                                            assignee={assignee}
                                            people={people}
                                            query={personQuery}
                                            onQueryChange={onPersonQueryChange}
                                            onUpdate={onUpdate} />
                )}

                {assignee.kind === "GROUP_ROLE" && (
                    <GroupRoleAssigneeFields index={index}
                                             assignee={assignee}
                                             groupRefOptions={groupRefOptions}
                                             onUpdate={onUpdate} />
                )}

            </div>
            <Button id={`assignee-remove-btn-${index}`}
                    className={styles.removeButton}
                    size="small"
                    appearance="subtle"
                    shape={"circular"}
                    icon={<DeleteIcon/>}
                    onClick={onRemove}
                    aria-label="Remove assignee" />
        </div>
    );
};

export default AssigneeRow;
