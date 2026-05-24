import React, {createContext, ReactNode, useCallback, useContext, useEffect, useRef, useState} from 'react';
import {fetchAppUser, fetchAppUserPersonOrganization} from '../services/appUserApi.ts';
import {AppUserDetailedDto, OrganizationDetailedDto} from "../app/models/models.tsx";
import {getTokenSecondsToExpiry, isTokenExpired} from "../utils/helpers.ts";
import {useLocation, useNavigate} from "react-router-dom";
import {setApiClientAuthToken} from "../services/apiClient.ts";
import {refreshTokens as refreshTokensApi} from "../services/authApi.ts";

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
    refreshTokens: () => Promise<void>;
    /**
     * True until the initial refresh-token probe on mount has resolved.
     * Route guards should wait on this — otherwise a fresh browser session
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
    const [appUser, setAppUser] = useState<AppUserDetailedDto | null>(null);
    const [appUserPersonOrganization, setAppUserPersonOrganization] = useState<OrganizationDetailedDto | null>(null);
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
        try
        {
            const data = await refreshTokensApi();
            if (data?.accessToken)
            {
                setAccessToken(data.accessToken);
                if (data.idToken) setIdToken(data.idToken);
            }
        }
        catch
        {
            // No valid refresh token — user is not logged in
            console.log("No active session (refresh token unavailable)");
        }
    }, [setAccessToken, setIdToken]);

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
            const reason: string | undefined = (e as CustomEvent).detail?.reason;
            setAccessToken(null);
            setIdToken(null);
            setAppUser(null);
            setAppUserPersonOrganization(null);
            redirectToSessionExpired(reason);
        };

        window.addEventListener('tokens-refreshed', handleTokensRefreshed);
        window.addEventListener('auth-session-expired', handleSessionExpired);

        return () =>
        {
            window.removeEventListener('tokens-refreshed', handleTokensRefreshed);
            window.removeEventListener('auth-session-expired', handleSessionExpired);
        };
    }, [setAccessToken, setIdToken]);

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
                catch
                {
                    setAccessToken(null);
                    setIdToken(null);
                    redirectToSessionExpired();
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
            return;
        }

        if (isTokenExpired(accessToken))
        {
            // Will be handled by the interval / interceptor
            return;
        }

        fetchUserData();
    }, [accessToken, appUser, location.pathname]);

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
                try
                {
                    setAppUserPersonOrganization(await fetchAppUserPersonOrganization(appUser?.id, appUser?.person?.id, accessToken));
                }
                catch (error: any)
                {
                    if (error.response?.status === 404)
                    {
                        console.log("Organization not found for user");
                    }
                }
            }
        }
        catch (error: any)
        {
            console.error("Failed to fetch user data:", error);
            setAccessToken(null);
            setIdToken(null);
            redirectToLogin();
        }
    };

    const redirectToLogin = () =>
    {
        const publicPaths = ["/sign-in", "/sign-up", "/account-recovery", "/nas", "/app-session-expired", "/oauth/callback", "/oauth/link-confirm"];
        if (!publicPaths.includes(location.pathname))
        {
            navigate("/sign-in");
        }
    };

    const redirectToSessionExpired = (reason?: string) =>
    {
        if (!["/app-session-expired"].includes(location.pathname))
        {
            const path = reason ? `/app-session-expired?reason=${encodeURIComponent(reason)}` : "/app-session-expired";
            navigate(path);
        }
    }

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
                refreshTokens,
                isBootstrapping,
            }}>
            {children}
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