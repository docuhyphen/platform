import {Select} from "@fluentui/react-components";
import {AssigneeSpecDraft} from "../../../../models/models.tsx";
import {AssigneeOption, GROUP_ROLES} from "../assigneeOptions.ts";
import {useGroupRoleAssigneeFieldsStyles} from "./GroupRoleAssigneeFieldsStyles.tsx";

interface GroupRoleAssigneeFieldsProps
{
    index: number;
    assignee: AssigneeSpecDraft;
    groupRefOptions: AssigneeOption[];
    onUpdate: (patch: Partial<AssigneeSpecDraft>) => void;
}

const GroupRoleAssigneeFields = ({index, assignee, groupRefOptions, onUpdate}: GroupRoleAssigneeFieldsProps) =>
{
    const styles = useGroupRoleAssigneeFieldsStyles();

    return (
        <>
            <Select id={`assignee-group-id-select-${index}`}
                    className={styles.fieldInput}
                    value={assignee.groupIdRef ?? ""}
                    onChange={(_, d) => onUpdate({groupIdRef: d.value})}
                    size="small">
                <option id={"assignee-builder-option-10"}
                        value="">Select group...</option>
                {groupRefOptions.map(r => (
                    <option id={`assignee-builder-option-11-${r.value}`}
                            key={r.value}
                            value={r.value}>{r.label}</option>
                ))}
            </Select>
            <Select id={`assignee-group-role-select-${index}`}
                    className={styles.kindSelect}
                    value={assignee.groupRole ?? "MANAGER"}
                    onChange={(_, d) => onUpdate({groupRole: d.value})}
                    size="small">
                {GROUP_ROLES.map(r => (
                    <option id={`assignee-builder-option-12-${r.value}`}
                            key={r.value}
                            value={r.value}>{r.label}</option>
                ))}
            </Select>
        </>
    );
};

export default GroupRoleAssigneeFields;
