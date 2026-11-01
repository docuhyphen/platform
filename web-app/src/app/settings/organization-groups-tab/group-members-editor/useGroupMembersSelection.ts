import {useRef, useState} from "react";
import {useAuth} from "../../../../context/AuthContext.tsx";
import {fetchMyOrganizationUsers} from "../../../../services/organizationApi.ts";
import {AppUserPublicDto} from "../../../models/models.tsx";
import {PrincipalGroupRoleName} from "../../../../services/types/roles";
import {MemberRoles} from "./groupMembersSelection.ts";

interface GroupMembersSelectionOptions
{
    defaultRoleFor?: (uid: string) => PrincipalGroupRoleName;
    onLoadError: (message: string) => void;
}

export interface GroupMembersSelection
{
    selectedMembers: Set<string>;
    memberRoles: MemberRoles;
    loadingUsers: boolean;
    query: string;
    pickerOptions: AppUserPublicDto[];
    memberUsers: AppUserPublicDto[];
    loadUsers: () => Promise<AppUserPublicDto[]>;
    reset: () => void;
    resetQuery: () => void;
    setSelection: (members: Set<string>, roles: MemberRoles) => void;
    onQueryChange: (query: string) => void;
    onMemberSelectionChange: (selectedIds: string[]) => void;
    setMemberRole: (uid: string, role: PrincipalGroupRoleName) => void;
    removeMember: (uid: string) => void;
}

const memberRole = () => PrincipalGroupRoleName.MEMBER;

export const useGroupMembersSelection = (
    {defaultRoleFor = memberRole, onLoadError}: GroupMembersSelectionOptions,
): GroupMembersSelection =>
{
    const {token} = useAuth();
    const [users, setUsers] = useState<AppUserPublicDto[]>([]);
    const [selectedMembers, setSelectedMembers] = useState<Set<string>>(new Set());
    const [memberRoles, setMemberRoles] = useState<MemberRoles>(new Map());
    const [loadingUsers, setLoadingUsers] = useState(false);
    const [query, setQuery] = useState("");
    const [filteredQuery, setFilteredQuery] = useState("");
    const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

    const loadUsers = async (): Promise<AppUserPublicDto[]> =>
    {
        setLoadingUsers(true);
        try
        {
            const fetched = await fetchMyOrganizationUsers(token || undefined);
            const active = fetched.filter(u => u.isActive);
            setUsers(active);
            return active;
        }
        catch (err: unknown)
        {
            onLoadError(err instanceof Error && err.message ? err.message : "Failed to load users");
            console.error("Failed to load users:", err);
            return [];
        }
        finally
        {
            setLoadingUsers(false);
        }
    };

    const resetQuery = () =>
    {
        setQuery("");
        setFilteredQuery("");
    };

    const reset = () =>
    {
        setSelectedMembers(new Set());
        setMemberRoles(new Map());
        resetQuery();
    };

    const setSelection = (members: Set<string>, roles: MemberRoles) =>
    {
        setSelectedMembers(members);
        setMemberRoles(roles);
    };

    const addMember = (uid: string) =>
    {
        setSelectedMembers(prev =>
        {
            const next = new Set(prev);
            next.add(uid);
            return next;
        });
        setMemberRoles(prev =>
        {
            const next = new Map(prev);
            if (!next.has(uid)) next.set(uid, defaultRoleFor(uid));
            return next;
        });
    };

    const removeMember = (uid: string) =>
    {
        setSelectedMembers(prev =>
        {
            const next = new Set(prev);
            next.delete(uid);
            return next;
        });
    };

    const setMemberRole = (uid: string, role: PrincipalGroupRoleName) =>
    {
        setMemberRoles(prev =>
        {
            const next = new Map(prev);
            next.set(uid, role);
            return next;
        });
    };

    const onQueryChange = (nextQuery: string) =>
    {
        setQuery(nextQuery);
        if (debounceRef.current) clearTimeout(debounceRef.current);
        debounceRef.current = setTimeout(() => setFilteredQuery(nextQuery), 150);
    };

    const pickerOptions = users.filter(u =>
    {
        if (selectedMembers.has(u.id)) return false;
        if (!filteredQuery) return true;
        const q = filteredQuery.toLowerCase();
        const fullName = `${u.person?.firstName ?? ""} ${u.person?.lastName ?? ""}`.toLowerCase();
        return fullName.includes(q) || u.email.toLowerCase().includes(q);
    });

    const memberUsers = users.filter(u => selectedMembers.has(u.id));

    const onMemberSelectionChange = (selectedIds: string[]) =>
    {
        const currentIds = Array.from(selectedMembers);
        selectedIds.filter(id => !selectedMembers.has(id)).forEach(addMember);
        currentIds.filter(id => !selectedIds.includes(id)).forEach(removeMember);
        if (selectedIds.length > currentIds.length) resetQuery();
    };

    return {
        selectedMembers,
        memberRoles,
        loadingUsers,
        query,
        pickerOptions,
        memberUsers,
        loadUsers,
        reset,
        resetQuery,
        setSelection,
        onQueryChange,
        onMemberSelectionChange,
        setMemberRole,
        removeMember,
    };
};
