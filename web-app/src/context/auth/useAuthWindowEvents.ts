import {MutableRefObject, useEffect} from 'react';
import {BroadcastAuthEvent, ClearAuthStateAndRedirect} from "./authContextTypes.ts";
import {sessionExpiredPath} from "./sessionEndReason.ts";

interface AuthWindowEventsOptions
{
    setAccessToken: (token: string | null) => void;
    setIdToken: (token: string | null) => void;
    clearAuthStateAndRedirect: ClearAuthStateAndRedirect;
    broadcastAuthEvent: BroadcastAuthEvent;
    suppressNextLogoutBroadcast: () => void;
    sessionEndReasonRef: MutableRefObject<string | null>;
}

export const useAuthWindowEvents = (options: AuthWindowEventsOptions) =>
{
    const {
        setAccessToken,
        setIdToken,
        clearAuthStateAndRedirect,
        broadcastAuthEvent,
        suppressNextLogoutBroadcast,
        sessionEndReasonRef,
    } = options;

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
            suppressNextLogoutBroadcast();
            clearAuthStateAndRedirect(sessionExpiredPath(reason));
        };

        const handleSignInRequired = () =>
        {
            broadcastAuthEvent('logout');
            suppressNextLogoutBroadcast();
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
    }, [
        setAccessToken,
        setIdToken,
        broadcastAuthEvent,
        clearAuthStateAndRedirect,
        suppressNextLogoutBroadcast,
        sessionEndReasonRef,
    ]);
};
