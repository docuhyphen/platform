import React, {createContext, ReactNode, useCallback, useContext, useEffect, useRef, useState} from 'react';
import {fetchAppUser, fetchAppUserPersonOrganization} from '../services/appUserApi.ts';
import {AppUserDetailedDto, OrganizationDetailedDto} from "../app/models/models.tsx";
import {isTokenExpired} from "../utils/helpers.ts";
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

    // On mount, attempt to refresh tokens if we don't have a valid access token
    useEffect(() =>
    {
        const doInitialRefresh = async () =>
        {
            if (!accessToken || isTokenExpired(accessToken))
            {
                await refreshTokens();
            }
            else
            {
                setApiClientAuthToken(accessToken);
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

        const handleSessionExpired = () =>
        {
            setAccessToken(null);
            setIdToken(null);
            setAppUser(null);
            setAppUserPersonOrganization(null);
            redirectToSessionExpired();
        };

        window.addEventListener('tokens-refreshed', handleTokensRefreshed);
        window.addEventListener('auth-session-expired', handleSessionExpired);

        return () =>
        {
            window.removeEventListener('tokens-refreshed', handleTokensRefreshed);
            window.removeEventListener('auth-session-expired', handleSessionExpired);
        };
    }, [setAccessToken, setIdToken]);

    // Token expiration polling (check every 30s, attempt refresh before expiry)
    useEffect(() =>
    {
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

    const redirectToSessionExpired = () =>
    {
        if (!["/app-session-expired"].includes(location.pathname))
        {
            navigate("/app-session-expired");
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