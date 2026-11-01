import {useCallback, useEffect, useRef} from 'react';
import {AUTH_EVENT_STORAGE_KEY, AUTH_USER_STORAGE_KEY} from "./authStorageKeys.ts";
import {AuthBroadcastEventType, BroadcastAuthEvent} from "./authContextTypes.ts";

interface AuthBroadcastOptions
{
    accessToken: string | null;
    appUserId: string | undefined;
}

export const useAuthBroadcast = ({accessToken, appUserId}: AuthBroadcastOptions) =>
{
    const tabIdRef = useRef<string>(`${Date.now()}-${Math.random().toString(36).slice(2)}`);
    const suppressNextLogoutBroadcastRef = useRef(false);
    const previousAccessTokenRef = useRef<string | null>(accessToken);
    const sessionEndReasonRef = useRef<string | null>(null);

    const broadcastAuthEvent = useCallback<BroadcastAuthEvent>((
        type: AuthBroadcastEventType,
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

    const suppressNextLogoutBroadcast = useCallback(() =>
    {
        suppressNextLogoutBroadcastRef.current = true;
    }, []);

    useEffect(() =>
    {
        if (accessToken && appUserId)
        {
            localStorage.setItem(AUTH_USER_STORAGE_KEY, appUserId);
            broadcastAuthEvent('user-change', appUserId);
        }
    }, [accessToken, appUserId, broadcastAuthEvent]);

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

    return {tabIdRef, sessionEndReasonRef, broadcastAuthEvent, suppressNextLogoutBroadcast};
};
