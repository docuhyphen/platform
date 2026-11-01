import {Select} from "@fluentui/react-components";
import {APP_ROLES} from "../assigneeOptions.ts";
import {useAppRoleAssigneeSelectStyles} from "./AppRoleAssigneeSelectStyles.tsx";

interface AppRoleAssigneeSelectProps
{
    index: number;
    roleName: string | undefined;
    onRoleChange: (roleName: string) => void;
}

const AppRoleAssigneeSelect = ({index, roleName, onRoleChange}: AppRoleAssigneeSelectProps) =>
{
    const styles = useAppRoleAssigneeSelectStyles();

    return (
        <Select id={`assignee-app-role-name-select-${index}`}
                className={styles.fieldInput}
                value={roleName ?? ""}
                onChange={(_, d) => onRoleChange(d.value)}
                size="small">
            <option id={"assignee-builder-option-5"}
                    value="">Select app role...</option>
            {APP_ROLES.map(role => (
                <option id={`assignee-builder-option-6-${role.value}`}
                        key={role.value}
                        value={role.value}>
                    {role.label}
                </option>
            ))}
        </Select>
    );
};

export default AppRoleAssigneeSelect;
