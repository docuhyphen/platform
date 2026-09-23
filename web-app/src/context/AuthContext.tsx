import React, {createContext, ReactNode, useCallback, useContext, useEffect, useRef, useState} from 'react';
import {fetchAppUser, fetchAppUserPersonOrganization, fetchCurrentSession} from '../services/appUserApi.ts';
import {fetchAppUserAvatarObjectUrl} from '../services/appUserAvatarApi.ts';
import {AppUserDetailedDto, Capability, CurrentSessionDto, OrganizationDetailedDto, SessionOrganizationOptionDto} from "../app/models/models.tsx";
import {getTokenSecondsToExpiry, isTokenExpired} from "../utils/helpers.ts";
import {useLocation, useNavigate} from "react-router-dom";
import {setApiClientAuthToken, setApiClientActiveOrganizationId} from "../services/apiClient.ts";
import {refreshTokens as refreshTokensApi} from "../services/authApi.ts";
import OrganizationPickerDialog from "../app/components/organization-picker/OrganizationPickerDialog.tsx";
import SessionInactivityGuard from "../app/components/session-expiry-warning/SessionInactivityGuard.tsx";

const AUTH_EVENT_STORAGE_KEY = 'docuhyphen:auth:event';
const AUTH_USER_STORAGE_KEY = 'docuhyphen:auth:user-id';
const AUTH_ACTIVE_ORG_STORAGE_KEY = 'docuhyphen:auth:active-org-id';

function readSessionEndReason(error: unknown): string | undefined
{
    if (typeof error !== 'object' || error === null || !('reasonCode' in error)) return undefined;
    const reasonCode = error.reasonCode;
    return typeof reasonCode === 'string' && reasonCode.length > 0 ? reasonCode : undefined;
}

/**
 * Decides how the active organization should be resolved for a freshly fetched session.
 * Pure and side-effect free so it can be unit-tested independently of React state.
 *
 * - `none`: nothing to do (personal mode, or the server already honored a valid active org).
 * - `auto`: exactly one membership and no active org yet, select it silently.
 * - `picker`: multiple memberships and no active org yet, the caller must choose.
 */
export type OrgSelectionResolution =
    | { kind: "none" }
    | { kind: "auto"; organizationId: string }
    | { kind: "picker"; organizations: SessionOrganizationOptionDto[] };

export function resolveOrgSelection(session: CurrentSessionDto): OrgSelectionResolution
{
    const orgs = session.availableOrganizations ?? [];
    if (orgs.length === 0)
    {
        return {kind: "none"};
    }
    if (session.activeOrganizationId)
    {
        return {kind: "none"};
    }
    if (orgs.length === 1)
    {
        return {kind: "auto", organizationId: orgs[0].organizationId};
    }
    return {kind: "picker", organizations: orgs};
}

interface AuthContextType
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
    /**
     * Current-session contract: user identity, explicitly selected organization,
     * applicable scoped roles, and effective capabilities. Null until the user is
     * authenticated and the session endpoint has been fetched.
     */
    currentSession: CurrentSessionDto | null;
    /**
     * True when the caller's current session includes the given capability.
     * Returns false when no session has been loaded yet.
     */
    hasCapability: (cap: Capability) => boolean;
    /** Re-fetches capabilities, subscription, and usage without replacing identity tokens. */
    refreshCurrentSession: () => Promise<CurrentSessionDto | null>;
    /**
     * Switch the active organization. Stores the selection, updates the API client header,
     * and re-fetches the current session so capabilities reflect the new context immediately.
     * Pass null to clear the active org (personal-product mode).
     */
    switchOrganization: (orgId: string | null) => Promise<void>;
    refreshTokens: () => Promise<void>;
    /**
     * True until the initial refresh-token probe on mount has resolved.
     * Route guards should wait on this,  otherwise a fresh browser session
     * (sessionStorage empty) bounces through /sign-in for a frame while the
     * refresh is still in flight.
     */
    isBootstrapping: boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: ReactNode }> = ({children}) =>
{
    const navigate = useNavigate();
    const location = useLocation();

    // Keep backward-compatible `token` (alias for accessToken) + new accessToken/idToken
    const [accessToken, setAccessTokenState] = useState<string | null>(() => sessionStorage.getItem('accessToken'));
    const [idToken, setIdTokenState] = useState<string | null>(() => sessionStorage.getItem('idToken'));
    const tokenExpirationIntervalRef = useRef<number | null>(null);
    const tabIdRef = useRef<string>(`${Date.now()}-${Math.random().toString(36).slice(2)}`);
    const suppressNextLogoutBroadcastRef = useRef(false);
    const previousAccessTokenRef = useRef<string | null>(accessToken);
    const sessionEndReasonRef = useRef<string | null>(null);
    const [appUser, setAppUser] = useState<AppUserDetailedDto | null>(null);
    const [appUserPersonOrganization, setAppUserPersonOrganization] = useState<OrganizationDetailedDto | null>(null);
    const [currentSession, setCurrentSession] = useState<CurrentSessionDto | null>(null);
    const [orgPickerOptions, setOrgPickerOptions] = useState<SessionOrganizationOptionDto[] | null>(null);
    const switchOrganizationRef = useRef<((orgId: string | null) => Promise<void>) | null>(null);

    // Apply the org-selection decision for a freshly fetched session: auto-select a lone org,
    // prompt for a choice when several exist, or do nothing. Idempotent by construction, once an
    // active org is set the resolution short-circuits to `none`, which prevents loops.
    const applyOrgResolution = useCallback((session: CurrentSessionDto) =>
    {
        const resolution = resolveOrgSelection(session);
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

    // Restore and apply the persisted active org selection on mount so every API request
    // carries the correct header before the first fetch completes.
    useEffect(() =>
    {
        const storedOrgId = localStorage.getItem(AUTH_ACTIVE_ORG_STORAGE_KEY);
        setApiClientActiveOrganizationId(storedOrgId);
    }, []);
    // Start bootstrapping whenever we don't have a usable access token in sessionStorage.
    // If we already have one (in-tab reload), we can render immediately.
    const [isBootstrapping, setIsBootstrapping] = useState<boolean>(() =>
    {
        const cached = sessionStorage.getItem('accessToken');
        return !cached || isTokenExpired(cached);
    });

    // Convenience alias
    const token = accessToken;

    const setAccessToken = useCallback((t: string | null) =>
    {
        setAccessTokenState(t);
        if (t)
        {
            sessionStorage.setItem('accessToken', t);
        }
        else
        {
            sessionStorage.removeItem('accessToken');
        }
        setApiClientAuthToken(t);
    }, []);

    const setIdToken = useCallback((t: string | null) =>
    {
        setIdTokenState(t);
        if (t) sessionStorage.setItem('idToken', t);
        else sessionStorage.removeItem('idToken');
    }, []);

    const setToken = useCallback((t: string | null) =>
    {
        setAccessToken(t);
        if (!t)
        {
            setIdToken(null);
        }
    }, [setAccessToken, setIdToken]);

    // Try to refresh tokens on app load (cookie-based)
    const refreshTokens = useCallback(async () =>
    {
        const data = await refreshTokensApi();
        if (data?.accessToken)
        {
            setAccessToken(data.accessToken);
            if (data.idToken) setIdToken(data.idToken);
        }
    }, [setAccessToken, setIdToken]);

    const clearAuthStateAndRedirect = useCallback((path: string = '/sign-in') =>
    {
        setAccessToken(null);
        setIdToken(null);
        setAppUser(null);
        setAppUserPersonOrganization(null);
        setCurrentSession(null);
        localStorage.removeItem(AUTH_ACTIVE_ORG_STORAGE_KEY);
        setApiClientActiveOrganizationId(null);
        navigate(path);
    }, [setAccessToken, setIdToken, navigate]);

    const broadcastAuthEvent = useCallback((
        type: 'logout' | 'user-change',
        userId?: string | null,
        reason?: string | null,
    ) =>
    {
        localStorage.setItem(
            AUTH_EVENT_STORAGE_KEY,
            JSON.stringify({
                type,
                userId: userId ?? null,
                reason: reason ?? null,
                sourceTabId: tabIdRef.current,
                ts: Date.now(),
            })
        );
    }, []);

    // On mount, attempt to refresh tokens if we don't have a valid access token.
    // Route guards block on `isBootstrapping` until this completes, so a closed-then-reopened
    // browser doesn't flash the sign-in page while the cookie-based refresh is in flight.
    useEffect(() =>
    {
        const doInitialRefresh = async () =>
        {
            try
            {
                if (!accessToken || isTokenExpired(accessToken))
                {
                    await refreshTokens();
                }
                else
                {
                    setApiClientAuthToken(accessToken);
                }
            }
            catch
            {
                console.log("No active session (refresh token unavailable)");
            }
            finally
            {
                setIsBootstrapping(false);
            }
        };
        doInitialRefresh();
    }, []);

    // Listen for token-refreshed events from the axios interceptor
    useEffect(() =>
    {
        const handleTokensRefreshed = (e: Event) =>
        {
            const detail = (e as CustomEvent).detail;
            if (detail?.accessToken) setAccessToken(detail.accessToken);
            if (detail?.idToken) setIdToken(detail.idToken);
        };

        const handleSessionExpired = (e: Event) =>
        {
            const incomingReason: string | undefined = (e as CustomEvent).detail?.reason;
            if (incomingReason) sessionEndReasonRef.current = incomingReason;
            const reason = incomingReason ?? sessionEndReasonRef.current ?? undefined;
            broadcastAuthEvent('logout', null, reason);
            suppressNextLogoutBroadcastRef.current = true;
            setAccessToken(null);
            setIdToken(null);
            setAppUser(null);
            setAppUserPersonOrganization(null);
            setCurrentSession(null);
            localStorage.removeItem(AUTH_ACTIVE_ORG_STORAGE_KEY);
            setApiClientActiveOrganizationId(null);
            const path = reason
                ? `/app-session-expired?reason=${encodeURIComponent(reason)}`
                : '/app-session-expired';
            navigate(path);
        };

        const handleSignInRequired = () =>
        {
            broadcastAuthEvent('logout');
            suppressNextLogoutBroadcastRef.current = true;
            clearAuthStateAndRedirect('/sign-in');
        };

        window.addEventListener('tokens-refreshed', handleTokensRefreshed);
        window.addEventListener('auth-session-expired', handleSessionExpired);
        window.addEventListener('auth-sign-in-required', handleSignInRequired);

        return () =>
        {
            window.removeEventListener('tokens-refreshed', handleTokensRefreshed);
            window.removeEventListener('auth-session-expired', handleSessionExpired);
            window.removeEventListener('auth-sign-in-required', handleSignInRequired);
        };
    }, [setAccessToken, setIdToken, broadcastAuthEvent, clearAuthStateAndRedirect, navigate]);

    useEffect(() =>
    {
        const handleStorageEvent = (event: StorageEvent) =>
        {
            if (event.key === AUTH_EVENT_STORAGE_KEY && event.newValue)
            {
                try
                {
                    const payload = JSON.parse(event.newValue) as {
                        type?: 'logout' | 'user-change';
                        userId?: string | null;
                        sourceTabId?: string;
                        reason?: string | null;
                    };

                    if (payload.sourceTabId === tabIdRef.current)
                    {
                        return;
                    }

                    if (payload.type === 'logout')
                    {
                        suppressNextLogoutBroadcastRef.current = true;
                        const path = payload.reason
                            ? `/app-session-expired?reason=${encodeURIComponent(payload.reason)}`
                            : '/sign-in';
                        clearAuthStateAndRedirect(path);
                        return;
                    }

                    if (payload.type === 'user-change')
                    {
                        const incomingUserId = payload.userId || null;
                        if (appUser?.id && incomingUserId && incomingUserId !== appUser.id)
                        {
                            suppressNextLogoutBroadcastRef.current = true;
                            clearAuthStateAndRedirect('/sign-in');
                        }
                    }
                }
                catch
                {
                    // Ignore malformed cross-tab payloads.
                }
            }

            if (event.key === AUTH_USER_STORAGE_KEY)
            {
                const incomingUserId = event.newValue || null;
                if (appUser?.id && incomingUserId && incomingUserId !== appUser.id)
                {
                    suppressNextLogoutBroadcastRef.current = true;
                    clearAuthStateAndRedirect('/sign-in');
                }
            }
        };

        window.addEventListener('storage', handleStorageEvent);
        return () => window.removeEventListener('storage', handleStorageEvent);
    }, [appUser?.id, clearAuthStateAndRedirect]);

    useEffect(() =>
    {
        if (token && appUser?.id)
        {
            localStorage.setItem(AUTH_USER_STORAGE_KEY, appUser.id);
            broadcastAuthEvent('user-change', appUser.id);
        }
    }, [token, appUser?.id, broadcastAuthEvent]);

    useEffect(() =>
    {
        if (accessToken) sessionEndReasonRef.current = null;
        const previousToken = previousAccessTokenRef.current;
        previousAccessTokenRef.current = accessToken;

        if (previousToken && !accessToken)
        {
            localStorage.removeItem(AUTH_USER_STORAGE_KEY);
            if (suppressNextLogoutBroadcastRef.current)
            {
                suppressNextLogoutBroadcastRef.current = false;
            }
            else
            {
                broadcastAuthEvent('logout');
            }
        }
    }, [accessToken, broadcastAuthEvent]);

    // Proactive silent refresh: schedule a refresh at ~80% of the access-token TTL.
    // Falls back to 30s polling as a safety net for tokens we can't decode.
    useEffect(() =>
    {
        if (!accessToken)
        {
            return;
        }

        const ttlSeconds = getTokenSecondsToExpiry(accessToken);
        const refreshAt = Math.max(5, ttlSeconds * 0.8); // never sooner than 5s, never on an expired token
        const proactiveTimerId = window.setTimeout(async () =>
        {
            try
            {
                await refreshTokens();
            }
            catch
            {
                // refreshTokens swallows errors; the interceptor + polling fallback will handle expiry
            }
        }, refreshAt * 1000);

        tokenExpirationIntervalRef.current = window.setInterval(async () =>
        {
            if (accessToken && isTokenExpired(accessToken))
            {
                try
                {
                    await refreshTokens();
                }
                catch (error: unknown)
                {
                    const reason = readSessionEndReason(error);
                    window.dispatchEvent(new CustomEvent('auth-session-expired', {
                        detail: reason ? {reason} : undefined,
                    }));
                }
            }
        }, 30000);

        return () =>
        {
            clearTimeout(proactiveTimerId);
            if (tokenExpirationIntervalRef.current)
            {
                clearInterval(tokenExpirationIntervalRef.current);
            }
        };
    }, [accessToken, refreshTokens]);

    useEffect(() =>
    {
        if (!accessToken)
        {
            setAppUser(null);
            setAppUserPersonOrganization(null);
            setCurrentSession(null);
            return;
        }

        if (isTokenExpired(accessToken))
        {
            // Will be handled by the interval / interceptor
            return;
        }

        fetchUserData();
    }, [accessToken, appUser, location.pathname]);


    useEffect(() =>
    {
        if (!appUser?.id || !accessToken)
        {
            return;
        }

        // avatarUrl arrives from the API as a relative marker path when a profile
        // picture exists. Swap it for an authenticated blob object URL the browser
        // can render directly.
        const marker = appUser.avatarUrl;
        if (!marker || marker.startsWith("blob:"))
        {
            return;
        }

        let cancelled = false;
        (async () =>
        {
            try
            {
                const objectUrl = await fetchAppUserAvatarObjectUrl(accessToken);
                if (cancelled)
                {
                    if (objectUrl)
                    {
                        URL.revokeObjectURL(objectUrl);
                    }
                    return;
                }
                setAppUser(prev => (prev ? {...prev, avatarUrl: objectUrl} : prev));
            }
            catch
            {
                // Non-fatal: the UI falls back to the user's initials.
            }
        })();

        return () =>
        {
            cancelled = true;
        };
    }, [appUser?.id, appUser?.avatarUrl, accessToken]);

    const fetchUserData = async () =>
    {
        try
        {
            if (!appUser && accessToken)
            {
                const user = await fetchAppUser(accessToken);
                setAppUser(user);
            }

            if (appUser && !appUser.person)
            {
                navigate("/onboarding/individual");
                return;
            }

            if (appUser?.person && !appUserPersonOrganization && accessToken)
            {
                if (appUser.organizationRoles.length === 0)
                {
                    // Individual users do not necessarily belong to an organization.
                }
                else
                {
                    try
                    {
                        setAppUserPersonOrganization(await fetchAppUserPersonOrganization(appUser?.id, appUser?.person?.id, accessToken));
                    }
                    catch (error: unknown)
                    {
                        if (error.response?.status === 404)
                        {
                            console.log("Organization not found for user");
                        }
                    }
                }
            }

            if (appUser && !currentSession)
            {
                try
                {
                    const session = await fetchCurrentSession();
                    setCurrentSession(session);
                    applyOrgResolution(session);
                }
                catch (error: unknown)
                {
                    console.error("Failed to fetch current session:", error);
                }
            }
        }
        catch (error: unknown)
        {
            console.error("Failed to fetch user data:", error);
            setAccessToken(null);
            setIdToken(null);
            redirectToLogin();
        }
    };

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
        }
        else
        {
            localStorage.removeItem(AUTH_ACTIVE_ORG_STORAGE_KEY);
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

    const redirectToLogin = () =>
    {
        const publicPaths = ["/sign-in", "/sign-up", "/account-recovery", "/nas", "/app-session-expired", "/oauth/callback", "/oauth/link-confirm"];
        if (!publicPaths.includes(location.pathname))
        {
            navigate("/sign-in");
        }
    };

    return (
        <AuthContext.Provider
            value={{
                token,
                setToken,
                accessToken,
                idToken,
                setAccessToken,
                setIdToken,
                appUser,
                setAppUser,
                appUserPersonOrganization,
                setAppUserPersonOrganization,
                currentSession,
                hasCapability,
                refreshCurrentSession,
                switchOrganization,
                refreshTokens,
                isBootstrapping,
            }}>
            {children}
            <OrganizationPickerDialog isOpen={orgPickerOptions !== null}
                                      organizations={orgPickerOptions ?? []}
                                      onSelect={onOrgPickerSelect}/>
            <SessionInactivityGuard
                token={token}
                currentSession={currentSession}
                setToken={setToken}/>
        </AuthContext.Provider>
    );
};

export const useAuth = (): AuthContextType =>
{
    const context = useContext(AuthContext);

    if (!context)
    {
        throw new Error('useAuth must be used within an AuthProvider');
    }
    return context;
};
