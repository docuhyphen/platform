import {useCallback, useEffect, useState} from "react";
import {Button, Select, Text} from "@fluentui/react-components";
import {
    AppUserDetailedDto,
    AssigneeKind,
    AssigneeSpecDraft,
    WorkflowSubjectFieldDto,
} from "../../models/models.tsx";
import {useAssigneeBuilderStyles} from "./AssigneeBuilderStyles.tsx";
import {AddIcon, DeleteIcon} from "../../components/IconBundles.tsx";
import {useAuth} from "../../../context/AuthContext.tsx";
import {
    fetchOrganizationGroups,
    fetchOrganizationUsers,
    OrganizationGroupBasicDto,
} from "../../../services/organizationApi.ts";

const ORG_ROLES = [
    {value: "ORG_ADMIN", label: "Organization Admin"},
    {value: "ORG_MEMBER", label: "Organization Member"},
    {value: "ORG_GROUP_ADMIN", label: "Group Admin"},
];

const APP_ROLES = [
    {value: "APP_ADMIN", label: "App Admin"},
    {value: "APP_USER", label: "App User"},
];

const GROUP_ROLES = [
    {value: "OWNER", label: "Owner"},
    {value: "MANAGER", label: "Manager"},
    {value: "MEMBER", label: "Member"},
    {value: "OBSERVER", label: "Observer"},
];

interface Props
{
    assignees: AssigneeSpecDraft[];
    onChange: (assignees: AssigneeSpecDraft[]) => void;
    label?: string;
    subjectFields?: WorkflowSubjectFieldDto[];
}

const userLabel = (u: AppUserDetailedDto): string =>
{
    const name = [u.person?.firstName, u.person?.lastName].filter(Boolean).join(' ');
    return name ? `${name} (${u.email})` : u.email;
};

const AssigneeBuilder = ({assignees, onChange, label, subjectFields = []}: Props) =>
{
    const styles = useAssigneeBuilderStyles();
    const {appUserPersonOrganization} = useAuth();
    const orgId = appUserPersonOrganization?.id;

    const [users, setUsers] = useState<AppUserDetailedDto[]>([]);
    const [groups, setGroups] = useState<OrganizationGroupBasicDto[]>([]);

    const loadOrgData = useCallback(async () =>
    {
        if (!orgId) return;
        const [u, g] = await Promise.all([
            fetchOrganizationUsers(orgId).catch(() => [] as AppUserDetailedDto[]),
            fetchOrganizationGroups(orgId).catch(() => [] as OrganizationGroupBasicDto[]),
        ]);
        setUsers(u);
        setGroups(g);
    }, [orgId]);

    useEffect(() => { loadOrgData(); }, [loadOrgData]);

    const update = (index: number, patch: Partial<AssigneeSpecDraft>) =>
        onChange(assignees.map((a, i) => (i === index ? {...a, ...patch} : a)));

    const remove = (index: number) => onChange(assignees.filter((_, i) => i !== index));

    const add = () => onChange([
        ...assignees,
        {kind: "ROLE", roleName: "ORG_ADMIN", scopeType: "ORG", scopeIdRef: "$subject.orgId"},
    ]);

    // Subject-field placeholders for scope refs (all) and group refs (UUID fields)
    const scopeRefOptions = [
        {value: "$subject.orgId", label: "Caller's organization"},
        ...subjectFields
            .filter(f => f.name !== 'orgId')
            .map(f => ({value: `$subject.${f.name}`, label: `${f.description ?? f.name} ($subject.${f.name})`})),
    ];

    const groupRefOptions = [
        ...groups.map(g => ({value: g.id, label: g.name})),
        ...subjectFields
            .filter(f => f.type === 'UUID' && f.name.toLowerCase().includes('group'))
            .map(f => ({value: `$subject.${f.name}`, label: `${f.description ?? f.name} (from trigger)`})),
    ];

    const roleOptions = (scopeType?: string) => scopeType === 'APP' ? APP_ROLES : ORG_ROLES;

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
                            className={styles.kindSelect}
                            value={a.kind}
                            onChange={(_, d) => update(i, {kind: d.value as AssigneeKind})}
                            size="small"
                        >
                            <option value="ROLE">By Role</option>
                            <option value="PRINCIPAL">Specific User</option>
                            <option value="GROUP_ROLE">Group Members</option>
                        </Select>

                        {/* ROLE: scope type + role name select + scope ref */}
                        {a.kind === "ROLE" && (<>
                            <Select
                                value={a.scopeType ?? "ORG"}
                                onChange={(_, d) => update(i, {scopeType: d.value as "APP" | "ORG", roleName: ""})}
                                size="small"
                            >
                                <option value="ORG">Within organization</option>
                                <option value="APP">Platform-wide</option>
                            </Select>
                            <Select
                                className={styles.fieldInput}
                                value={a.roleName ?? ""}
                                onChange={(_, d) => update(i, {roleName: d.value})}
                                size="small"
                            >
                                <option value="">Select role...</option>
                                {roleOptions(a.scopeType).map(r => (
                                    <option key={r.value} value={r.value}>{r.label}</option>
                                ))}
                            </Select>
                            {a.scopeType !== "APP" && (
                                <Select
                                    className={styles.fieldInput}
                                    value={a.scopeIdRef ?? "$subject.orgId"}
                                    onChange={(_, d) => update(i, {scopeIdRef: d.value})}
                                    size="small"
                                >
                                    {scopeRefOptions.map(r => (
                                        <option key={r.value} value={r.value}>{r.label}</option>
                                    ))}
                                </Select>
                            )}
                        </>)}

                        {/* PRINCIPAL: user picker */}
                        {a.kind === "PRINCIPAL" && (
                            <Select
                                className={styles.fieldInput}
                                value={a.principalId ?? ""}
                                onChange={(_, d) => update(i, {principalId: d.value, principalKind: "APP_USER"})}
                                size="small"
                            >
                                <option value="">Select user...</option>
                                {users.map(u => (
                                    <option key={u.id} value={u.id ?? ""}>{userLabel(u)}</option>
                                ))}
                            </Select>
                        )}

                        {/* GROUP_ROLE: group picker + role */}
                        {a.kind === "GROUP_ROLE" && (<>
                            <Select
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
                        size="small"
                        appearance="subtle"
                        icon={<DeleteIcon/>}
                        onClick={() => remove(i)}
                        aria-label="Remove assignee"
                    />
                </div>
            ))}

            <div className={styles.addRow}>
                <Button size="small" appearance="outline" icon={<AddIcon/>} onClick={add}>
                    Add Assignee
                </Button>
            </div>
        </div>
    );
};

export default AssigneeBuilder;