import {useCallback, useEffect, useState} from "react";
import {AppUserPublicDto} from "../../../models/models.tsx";
import {useAuth} from "../../../../context/AuthContext.tsx";
import {
    fetchOrganizationGroups,
    fetchOrganizationUsers,
    OrganizationGroupBasicDto,
} from "../../../../services/organizationApi.ts";

export function useOrganizationDirectory(allowTenantDirectory: boolean)
{
    const {appUserPersonOrganization} = useAuth();
    const orgId = appUserPersonOrganization?.id;
    const [users, setUsers] = useState<AppUserPublicDto[]>([]);
    const [groups, setGroups] = useState<OrganizationGroupBasicDto[]>([]);

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

    return {users, groups};
}
