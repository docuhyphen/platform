import {Dropdown, Field, Option} from "@fluentui/react-components";
import {OrganizationRoleDisplayNames, OrganizationRoleName} from '../../../../services/types/roles.ts';

export interface AppUserRolesFieldIds
{
    field: string;
    dropdown: string;
    optionPrefix: string;
}

interface AppUserRolesFieldProps
{
    ids: AppUserRolesFieldIds;
    roles: OrganizationRoleName[];
    onRolesChange: (roles: OrganizationRoleName[]) => void;
}

const AppUserRolesField = ({ids, roles, onRolesChange}: AppUserRolesFieldProps) => (
    <Field id={ids.field}
           label="Roles"
           required>
        <Dropdown
            id={ids.dropdown}
            multiselect
            selectedOptions={roles}
            value={roles.map((role) => OrganizationRoleDisplayNames[role]).join(', ')}
            onOptionSelect={(_, data) =>
                onRolesChange(data.selectedOptions as OrganizationRoleName[])}
        >
            {Object.entries(OrganizationRoleDisplayNames).map(([role, label]) => (
                <Option
                    id={`${ids.optionPrefix}-${role}`}
                    key={role}
                    value={role}
                >
                    {label}
                </Option>
            ))}
        </Dropdown>
    </Field>
);

export default AppUserRolesField;
