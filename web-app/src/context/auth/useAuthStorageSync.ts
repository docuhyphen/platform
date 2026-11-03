import {RefObject, useEffect} from 'react';
import {AUTH_EVENT_STORAGE_KEY, AUTH_USER_STORAGE_KEY} from "./authStorageKeys.ts";
import {AuthBroadcastEventPayload, ClearAuthStateAndRedirect} from "./authContextTypes.ts";

interface AuthStorageSyncOptions
{
    appUserId: string | undefined;
    tabIdRef: RefObject<string>;
    suppressNextLogoutBroadcast: () => void;
    clearAuthStateAndRedirect: ClearAuthStateAndRedirect;
}

const parseAuthEventPayload = (value: string): AuthBroadcastEventPayload | null =>
{
    try
    {
        return JSON.parse(value) as AuthBroadcastEventPayload;
    }
    catch
    {
        return null;
    }
};

export const useAuthStorageSync = (options: AuthStorageSyncOptions) =>
{
    const {appUserId, tabIdRef, suppressNextLogoutBroadcast, clearAuthStateAndRedirect} = options;

    useEffect(() =>
    {
        const signOutForOtherUser = (incomingUserId: string | null) =>
        {
            if (appUserId && incomingUserId && incomingUserId !== appUserId)
            {
                suppressNextLogoutBroadcast();
                clearAuthStateAndRedirect('/sign-in');
            }
        };

        const handleAuthEvent = (payload: AuthBroadcastEventPayload) =>
        {
            if (payload.sourceTabId === tabIdRef.current)
            {
                return;
            }

            if (payload.type === 'logout')
            {
                suppressNextLogoutBroadcast();
                const path = payload.reason
                    ? `/app-session-expired?reason=${encodeURIComponent(payload.reason)}`
                    : '/sign-in';
                clearAuthStateAndRedirect(path);
                return;
            }

            if (payload.type === 'user-change')
            {
                signOutForOtherUser(payload.userId || null);
            }
        };

        const handleStorageEvent = (event: StorageEvent) =>
        {
            if (event.key === AUTH_EVENT_STORAGE_KEY && event.newValue)
            {
                const payload = parseAuthEventPayload(event.newValue);
                if (payload) handleAuthEvent(payload);
            }

            if (event.key === AUTH_USER_STORAGE_KEY)
            {
                signOutForOtherUser(event.newValue || null);
            }
        };

        window.addEventListener('storage', handleStorageEvent);
        return () => window.removeEventListener('storage', handleStorageEvent);
    }, [appUserId, clearAuthStateAndRedirect, suppressNextLogoutBroadcast, tabIdRef]);
};
