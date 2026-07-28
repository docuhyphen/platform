import {useCallback, useEffect, useState} from "react";
import {Button, Select, Text} from "@fluentui/react-components";
import {
    AppUserPublicDto,
    AssigneeKind,
    AssigneeSpecDraft,
    WorkflowSubjectFieldDto,
} from "../../../models/models.tsx";
import {useAssigneeBuilderStyles} from "./AssigneeBuilderStyles.tsx";
import {AddIcon, DeleteIcon} from "../../../components/IconBundles.tsx";
import {useAuth} from "../../../../context/AuthContext.tsx";
import {
    fetchOrganizationGroups,
    fetchOrganizationUsers,
    OrganizationGroupBasicDto,
} from "../../../../services/organizationApi.ts";
import SinglePersonPicker from "../../../components/person-picker/single-person-picker/SinglePersonPicker.tsx";
import {
    matchesPersonQuery,
    PersonPickerItem,
} from "../../../components/person-picker/personPickerTypes.ts";
import {
    AppRoleDisplayNames,
    AppRoleName,
    OrganizationRoleDisplayNames,
    OrganizationRoleName,
    PrincipalGroupRoleDisplayNames,
    PrincipalGroupRoleName,
} from "../../../../services/types/roles.ts";

const APP_ROLES = Object.values(AppRoleName).map(value => ({
    value,
    label: AppRoleDisplayNames[value],
}));

const ORGANIZATION_ROLES = Object.values(OrganizationRoleName).map(value => ({
    value,
    label: OrganizationRoleDisplayNames[value],
}));

const GROUP_ROLES = Object.values(PrincipalGroupRoleName).map(value => ({
    value,
    label: PrincipalGroupRoleDisplayNames[value],
}));

const createAssignee = (kind: AssigneeKind): AssigneeSpecDraft =>
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

interface Props
{
    assignees: AssigneeSpecDraft[];
    onChange: (assignees: AssigneeSpecDraft[]) => void;
    label?: string;
    subjectFields?: WorkflowSubjectFieldDto[];
    allowTenantDirectory?: boolean;
}

const toPersonPickerItem = (user: AppUserPublicDto): PersonPickerItem => ({
    id: user.id,
    email: user.email,
    firstName: user.person?.firstName,
    lastName: user.person?.lastName,
    avatarUrl: user.avatarUrl,
});

const AssigneeBuilder = ({
    assignees,
    onChange,
    label,
    subjectFields = [],
    allowTenantDirectory = true,
}: Props) =>
{
    const styles = useAssigneeBuilderStyles();
    const {appUserPersonOrganization} = useAuth();
    const orgId = appUserPersonOrganization?.id;

    const [users, setUsers] = useState<AppUserPublicDto[]>([]);
    const [groups, setGroups] = useState<OrganizationGroupBasicDto[]>([]);
    const [personQueries, setPersonQueries] = useState<Record<number, string>>({});

    const loadOrgData = useCallback(async () =>
    {
        if (!allowTenantDirectory || !orgId) return;
        const [u, g] = await Promise.all([
            fetchOrganizationUsers(orgId).catch(() => [] as AppUserPublicDto[]),
            fetchOrganizationGroups(orgId).catch(() => [] as OrganizationGroupBasicDto[]),
        ]);
        setUsers(u);
        setGroups(g);
    }, [allowTenantDirectory, orgId]);

    useEffect(() => { loadOrgData(); }, [loadOrgData]);

    const update = (index: number, patch: Partial<AssigneeSpecDraft>) =>
        onChange(assignees.map((a, i) => (i === index ? {...a, ...patch} : a)));

    const replace = (index: number, assignee: AssigneeSpecDraft) =>
        onChange(assignees.map((current, currentIndex) => currentIndex === index ? assignee : current));

    const remove = (index: number) => onChange(assignees.filter((_, i) => i !== index));

    const add = () => onChange([
        ...assignees,
        createAssignee("ORGANIZATION_ROLE"),
    ]);

    const organizationRefOptions = [
        {value: "$subject.orgId", label: "Caller's organization"},
        ...subjectFields
            .filter(f => f.name !== 'orgId')
            .map(f => ({value: `$subject.${f.name}`, label: `${f.description ?? f.name} (from trigger)`})),
    ];

    const groupRefOptions = [
        ...groups.map(g => ({value: g.id, label: g.name})),
        ...subjectFields
            .filter(f => f.type === 'UUID' && f.name.toLowerCase().includes('group'))
            .map(f => ({value: `$subject.${f.name}`, label: `${f.description ?? f.name} (from trigger)`})),
    ];

    const people = users.map(toPersonPickerItem).filter(person => person.id);

    return (
        <div className={styles.container}>
            {label && <Text size={200} weight="semibold">{label}</Text>}

            {assignees.length === 0 && (
                <Text className={styles.emptyHint} size={200}>No assignees added yet.</Text>
            )}

            {assignees.map((a, i) => (
                <div key={i} className={styles.row}>
                    <div className={styles.rowFields}>

                        {/* Kind selector */}
                        <Select
                            id={`assignee-kind-select-${i}`}
                            className={styles.kindSelect}
                            value={a.kind}
                            onChange={(_, d) => replace(i, createAssignee(d.value as AssigneeKind))}
                            size="small"
                        >
                            <option value="ORGANIZATION_ROLE">Organization Role</option>
                            <option value="APP_ROLE">App Role</option>
                            {allowTenantDirectory && <option value="PRINCIPAL">Specific User</option>}
                            {allowTenantDirectory && <option value="GROUP_ROLE">Group Members</option>}
                        </Select>

                        {a.kind === "APP_ROLE" && (
                            <Select
                                id={`assignee-app-role-name-select-${i}`}
                                className={styles.fieldInput}
                                value={a.roleName ?? ""}
                                onChange={(_, d) => update(i, {roleName: d.value})}
                                size="small"
                            >
                                <option value="">Select app role...</option>
                                {APP_ROLES.map(role => (
                                    <option
                                        key={role.value}
                                        value={role.value}
                                    >
                                        {role.label}
                                    </option>
                                ))}
                            </Select>
                        )}

                        {a.kind === "ORGANIZATION_ROLE" && (<>
                            <Select
                                id={`assignee-organization-role-name-select-${i}`}
                                className={styles.fieldInput}
                                value={a.roleName ?? ""}
                                onChange={(_, d) => update(i, {roleName: d.value})}
                                size="small"
                            >
                                <option value="">Select organization role...</option>
                                {ORGANIZATION_ROLES.map(role => (
                                    <option
                                        key={role.value}
                                        value={role.value}
                                    >
                                        {role.label}
                                    </option>
                                ))}
                            </Select>
                            <Select
                                id={`assignee-organization-id-ref-select-${i}`}
                                className={styles.fieldInput}
                                value={a.organizationIdRef ?? "$subject.orgId"}
                                onChange={(_, d) => update(i, {organizationIdRef: d.value})}
                                size="small"
                            >
                                {organizationRefOptions.map(reference => (
                                    <option
                                        key={reference.value}
                                        value={reference.value}
                                    >
                                        {reference.label}
                                    </option>
                                ))}
                            </Select>
                        </>)}

                        {/* PRINCIPAL: user picker */}
                        {a.kind === "PRINCIPAL" && (
                            <SinglePersonPicker
                                id={`assignee-principal-select-${i}`}
                                size={"small"}
                                people={people.filter(person => matchesPersonQuery(person, personQueries[i] ?? ""))}
                                query={personQueries[i] ?? ""}
                                onQueryChange={query => setPersonQueries(current => ({...current, [i]: query}))}
                                onPersonSelect={person => update(i, {
                                    principalId: person?.id ?? "",
                                    principalKind: "USER",
                                })}
                                placeholder="Find a user"
                                selectedPersonId={a.principalId}
                                noResultsText="No matching organization users found"
                            />
                        )}

                        {/* GROUP_ROLE: group picker + role */}
                        {a.kind === "GROUP_ROLE" && (<>
                            <Select
                                id={`assignee-group-id-select-${i}`}
                                className={styles.fieldInput}
                                value={a.groupIdRef ?? ""}
                                onChange={(_, d) => update(i, {groupIdRef: d.value})}
                                size="small"
                            >
                                <option value="">Select group...</option>
                                {groupRefOptions.map(r => (
                                    <option key={r.value} value={r.value}>{r.label}</option>
                                ))}
                            </Select>
                            <Select
                                id={`assignee-group-role-select-${i}`}
                                className={styles.kindSelect}
                                value={a.groupRole ?? "MANAGER"}
                                onChange={(_, d) => update(i, {groupRole: d.value})}
                                size="small"
                            >
                                {GROUP_ROLES.map(r => (
                                    <option key={r.value} value={r.value}>{r.label}</option>
                                ))}
                            </Select>
                        </>)}

                    </div>
                    <Button
                        id={`assignee-remove-btn-${i}`}
                        className={styles.removeButton}
                        size="small"
                        appearance="subtle"
                        shape={"circular"}
                        icon={<DeleteIcon/>}
                        onClick={() => remove(i)}
                        aria-label="Remove assignee"
                    />
                </div>
            ))}

            <div className={styles.addRow}>
                <Button
                        id={"add-assignee-button"}
                        size="small"
                        shape={"circular"}
                        appearance="secondary"
                        icon={<AddIcon/>}
                        onClick={add}>
                    Add Assignee
                </Button>
            </div>
        </div>
    );
};

export default AssigneeBuilder;
