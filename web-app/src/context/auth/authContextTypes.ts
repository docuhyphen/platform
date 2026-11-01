import {AppUserDetailedDto, Capability, CurrentSessionDto, OrganizationDetailedDto} from "../../app/models/models.tsx";

export interface AuthContextType
{
    token: string | null;
    setToken: (token: string | null) => void;
    accessToken: string | null;
    idToken: string | null;
    setAccessToken: (token: string | null) => void;
    setIdToken: (token: string | null) => void;
    appUser: AppUserDetailedDto | null;
    setAppUser: (user: AppUserDetailedDto | null) => void;
    appUserPersonOrganization: OrganizationDetailedDto | null;
    setAppUserPersonOrganization: (organization: OrganizationDetailedDto | null) => void;
    currentSession: CurrentSessionDto | null;
    hasCapability: (cap: Capability) => boolean;
    refreshCurrentSession: () => Promise<CurrentSessionDto | null>;
    switchOrganization: (orgId: string | null) => Promise<void>;
    refreshTokens: () => Promise<void>;
    isBootstrapping: boolean;
}

export type AuthBroadcastEventType = 'logout' | 'user-change';

export interface AuthBroadcastEventPayload
{
    type?: AuthBroadcastEventType;
    userId?: string | null;
    sourceTabId?: string;
    reason?: string | null;
}

export type BroadcastAuthEvent = (
    type: AuthBroadcastEventType,
    userId?: string | null,
    reason?: string | null,
) => void;

export type ClearAuthStateAndRedirect = (path?: string) => void;
