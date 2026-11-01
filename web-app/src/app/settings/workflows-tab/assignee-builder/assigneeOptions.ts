import {AppUserPublicDto, AssigneeKind, AssigneeSpecDraft, WorkflowSubjectFieldDto} from "../../../models/models.tsx";
import {OrganizationGroupBasicDto} from "../../../../services/organizationApi.ts";
import {PersonPickerItem} from "../../../components/person-picker/personPickerTypes.ts";
import {
    AppRoleDisplayNames,
    AppRoleName,
    OrganizationRoleDisplayNames,
    OrganizationRoleName,
    PrincipalGroupRoleDisplayNames,
    PrincipalGroupRoleName,
} from "../../../../services/types/roles.ts";

export interface AssigneeOption
{
    value: string;
    label: string;
}

export const APP_ROLES: AssigneeOption[] = Object.values(AppRoleName).map(value => ({
    value,
    label: AppRoleDisplayNames[value],
}));

export const ORGANIZATION_ROLES: AssigneeOption[] = Object.values(OrganizationRoleName).map(value => ({
    value,
    label: OrganizationRoleDisplayNames[value],
}));

export const GROUP_ROLES: AssigneeOption[] = Object.values(PrincipalGroupRoleName).map(value => ({
    value,
    label: PrincipalGroupRoleDisplayNames[value],
}));

export const createAssignee = (kind: AssigneeKind): AssigneeSpecDraft =>
{
    switch (kind)
    {
        case "APP_ROLE":
            return {kind, roleName: AppRoleName.APP_USER};
        case "ORGANIZATION_ROLE":
            return {
                kind,
                roleName: OrganizationRoleName.ORG_ADMIN,
                organizationIdRef: "$subject.orgId",
            };
        case "GROUP_ROLE":
            return {kind, groupRole: PrincipalGroupRoleName.MANAGER};
        case "PRINCIPAL":
            return {kind, principalKind: "USER"};
    }
};

export const toPersonPickerItem = (user: AppUserPublicDto): PersonPickerItem => ({
    id: user.id,
    email: user.email,
    firstName: user.person?.firstName,
    lastName: user.person?.lastName,
    avatarUrl: user.avatarUrl,
});

const subjectFieldOption = (field: WorkflowSubjectFieldDto): AssigneeOption => ({
    value: `$subject.${field.name}`,
    label: `${field.description ?? field.name} (from trigger)`,
});

export const buildOrganizationRefOptions = (subjectFields: WorkflowSubjectFieldDto[]): AssigneeOption[] => [
    {value: "$subject.orgId", label: "Caller's organization"},
    ...subjectFields
        .filter(f => f.name !== "orgId")
        .map(subjectFieldOption),
];

export const buildGroupRefOptions = (groups: OrganizationGroupBasicDto[], subjectFields: WorkflowSubjectFieldDto[]): AssigneeOption[] => [
    ...groups.map(g => ({value: g.id, label: g.name})),
    ...subjectFields
        .filter(f => f.type === "UUID" && f.name.toLowerCase().includes("group"))
        .map(subjectFieldOption),
];
