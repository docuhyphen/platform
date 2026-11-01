import {useCallback, useEffect, useRef, useState} from 'react';
import {getTokenSecondsToExpiry, isTokenExpired} from "../../utils/helpers.ts";
import {setApiClientAuthToken} from "../../services/apiClient.ts";
import {refreshTokens as refreshTokensApi} from "../../services/authApi.ts";
import {readSessionEndReason} from "./sessionEndReason.ts";

export const useAuthTokens = () =>
{
    const [accessToken, setAccessTokenState] = useState<string | null>(() => sessionStorage.getItem('accessToken'));
    const [idToken, setIdTokenState] = useState<string | null>(() => sessionStorage.getItem('idToken'));
    const tokenExpirationIntervalRef = useRef<number | null>(null);
    const [isBootstrapping, setIsBootstrapping] = useState<boolean>(() =>
    {
        const cached = sessionStorage.getItem('accessToken');
        return !cached || isTokenExpired(cached);
    });

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

    const refreshTokens = useCallback(async () =>
    {
        const data = await refreshTokensApi();
        if (data?.accessToken)
        {
            setAccessToken(data.accessToken);
            if (data.idToken) setIdToken(data.idToken);
        }
    }, [setAccessToken, setIdToken]);

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

    useEffect(() =>
    {
        if (!accessToken)
        {
            return;
        }

        const ttlSeconds = getTokenSecondsToExpiry(accessToken);
        const refreshAt = Math.max(5, ttlSeconds * 0.8);
        const proactiveTimerId = window.setTimeout(() =>
        {
            void refreshTokens().catch(() => undefined);
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

    return {
        accessToken,
        idToken,
        token: accessToken,
        isBootstrapping,
        setAccessToken,
        setIdToken,
        setToken,
        refreshTokens,
    };
};
