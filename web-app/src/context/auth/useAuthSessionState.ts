import {useCallback, useEffect, useRef, useState} from 'react';
import {
    AppUserDetailedDto,
    Capability,
    CurrentSessionDto,
    OrganizationDetailedDto,
    SessionOrganizationOptionDto,
} from "../../app/models/models.tsx";
import {fetchCurrentSession} from '../../services/appUserApi.ts';
import {setApiClientActiveOrganizationId} from "../../services/apiClient.ts";
import {AUTH_ACTIVE_ORG_STORAGE_KEY, AUTH_PERSONAL_MODE_STORAGE_KEY} from "./authStorageKeys.ts";
import {resolveOrgSelection} from "./orgSelection.ts";

export const useAuthSessionState = () =>
{
    const [appUser, setAppUser] = useState<AppUserDetailedDto | null>(null);
    const [appUserPersonOrganization, setAppUserPersonOrganization] = useState<OrganizationDetailedDto | null>(null);
    const [currentSession, setCurrentSession] = useState<CurrentSessionDto | null>(null);
    const [orgPickerOptions, setOrgPickerOptions] = useState<SessionOrganizationOptionDto[] | null>(null);
    const switchOrganizationRef = useRef<((orgId: string | null) => Promise<void>) | null>(null);

    const applyOrgResolution = useCallback((session: CurrentSessionDto) =>
    {
        const resolution = resolveOrgSelection(session, localStorage.getItem(AUTH_PERSONAL_MODE_STORAGE_KEY) === 'true');
        if (resolution.kind === "auto")
        {
            setOrgPickerOptions(null);
            void switchOrganizationRef.current?.(resolution.organizationId);
        }
        else if (resolution.kind === "picker")
        {
            setOrgPickerOptions(resolution.organizations);
        }
        else
        {
            setOrgPickerOptions(null);
        }
    }, []);

    useEffect(() =>
    {
        const storedOrgId = localStorage.getItem(AUTH_ACTIVE_ORG_STORAGE_KEY);
        setApiClientActiveOrganizationId(storedOrgId);
    }, []);

    const clearSession = useCallback(() =>
    {
        setAppUser(null);
        setAppUserPersonOrganization(null);
        setCurrentSession(null);
    }, []);

    const hasCapability = useCallback((cap: Capability): boolean =>
    {
        return currentSession?.capabilities?.includes(cap) === true;
    }, [currentSession]);

    const refreshCurrentSession = useCallback(async (): Promise<CurrentSessionDto | null> =>
    {
        try
        {
            const session = await fetchCurrentSession();
            setCurrentSession(session);
            applyOrgResolution(session);
            return session;
        }
        catch (error: unknown)
        {
            console.error("Failed to refresh current session:", error);
            return null;
        }
    }, [applyOrgResolution]);

    const switchOrganization = useCallback(async (orgId: string | null) =>
    {
        if (orgId)
        {
            localStorage.setItem(AUTH_ACTIVE_ORG_STORAGE_KEY, orgId);
            localStorage.removeItem(AUTH_PERSONAL_MODE_STORAGE_KEY);
        }
        else
        {
            localStorage.removeItem(AUTH_ACTIVE_ORG_STORAGE_KEY);
            localStorage.setItem(AUTH_PERSONAL_MODE_STORAGE_KEY, 'true');
        }
        setApiClientActiveOrganizationId(orgId);
        setCurrentSession(null);

        await refreshCurrentSession();
    }, [refreshCurrentSession]);

    useEffect(() =>
    {
        switchOrganizationRef.current = switchOrganization;
    }, [switchOrganization]);

    const onOrgPickerSelect = useCallback((organizationId: string) =>
    {
        setOrgPickerOptions(null);
        void switchOrganization(organizationId);
    }, [switchOrganization]);

    return {
        appUser,
        setAppUser,
        appUserPersonOrganization,
        setAppUserPersonOrganization,
        currentSession,
        setCurrentSession,
        orgPickerOptions,
        applyOrgResolution,
        clearSession,
        hasCapability,
        refreshCurrentSession,
        switchOrganization,
        onOrgPickerSelect,
    };
};

export type AuthSessionState = ReturnType<typeof useAuthSessionState>;
