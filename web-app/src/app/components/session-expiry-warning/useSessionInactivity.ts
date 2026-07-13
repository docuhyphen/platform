import {useCallback, useEffect, useRef, useState} from "react";

const WARNING_DURATION_MS = 60_000;
const ACTIVITY_WRITE_THROTTLE_MS = 1_000;
const ACTIVITY_HEARTBEAT_THROTTLE_MS = 15_000;
const ACTIVITY_STORAGE_KEY_PREFIX = "docuhyphen:session:last-activity:";

interface UseSessionInactivityOptions
{
    userId: string | null;
    idleTimeoutMinutes: number | null;
    onContinue: () => Promise<void>;
    onExpire: () => Promise<void>;
}

interface UseSessionInactivityResult
{
    isWarningOpen: boolean;
    secondsRemaining: number;
    isContinuing: boolean;
    errorMessage: string | null;
    continueSession: () => Promise<void>;
    expireSession: () => Promise<void>;
}

export function useSessionInactivity(
    {
        userId,
        idleTimeoutMinutes,
        onContinue,
        onExpire,
    }: UseSessionInactivityOptions
): UseSessionInactivityResult
{
    const [isWarningOpen, setIsWarningOpen] = useState(false);
    const [secondsRemaining, setSecondsRemaining] = useState(60);
    const [isContinuing, setIsContinuing] = useState(false);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);
    const warningOpenRef = useRef(false);
    const expiredRef = useRef(false);
    const lastActivityRef = useRef(Date.now());
    const lastHeartbeatRef = useRef(Date.now());
    const heartbeatInFlightRef = useRef(false);
    const warningTimerRef = useRef<number | null>(null);
    const expiryTimerRef = useRef<number | null>(null);
    const countdownTimerRef = useRef<number | null>(null);
    const onContinueRef = useRef(onContinue);
    const onExpireRef = useRef(onExpire);

    useEffect(() =>
    {
        onContinueRef.current = onContinue;
        onExpireRef.current = onExpire;
    }, [onContinue, onExpire]);

    const clearTimers = useCallback(() =>
    {
        if (warningTimerRef.current !== null) window.clearTimeout(warningTimerRef.current);
        if (expiryTimerRef.current !== null) window.clearTimeout(expiryTimerRef.current);
        if (countdownTimerRef.current !== null) window.clearInterval(countdownTimerRef.current);
        warningTimerRef.current = null;
        expiryTimerRef.current = null;
        countdownTimerRef.current = null;
    }, []);

    const expireSession = useCallback(async () =>
    {
        if (expiredRef.current) return;
        expiredRef.current = true;
        clearTimers();
        await onExpireRef.current();
    }, [clearTimers]);

    const showWarning = useCallback((expiresAt: number) =>
    {
        warningOpenRef.current = true;
        setIsWarningOpen(true);
        setErrorMessage(null);

        const updateCountdown = () =>
        {
            const remaining = Math.max(0, Math.ceil((expiresAt - Date.now()) / 1_000));
            setSecondsRemaining(remaining);
        };

        updateCountdown();
        countdownTimerRef.current = window.setInterval(updateCountdown, 1_000);
        expiryTimerRef.current = window.setTimeout(() => void expireSession(), Math.max(0, expiresAt - Date.now()));

        heartbeatInFlightRef.current = true;
        void onContinueRef.current()
            .catch(() => expireSession())
            .finally(() =>
            {
                heartbeatInFlightRef.current = false;
                lastHeartbeatRef.current = Date.now();
            });
    }, [expireSession]);

    const scheduleFrom = useCallback((lastActivityAt: number) =>
    {
        if (!idleTimeoutMinutes || idleTimeoutMinutes <= 0) return;

        clearTimers();
        const expiresAt = lastActivityAt + idleTimeoutMinutes * 60_000;
        const warningAt = expiresAt - WARNING_DURATION_MS;
        const now = Date.now();

        if (now >= expiresAt)
        {
            void expireSession();
            return;
        }

        if (now >= warningAt)
        {
            showWarning(expiresAt);
            return;
        }

        warningOpenRef.current = false;
        setIsWarningOpen(false);
        warningTimerRef.current = window.setTimeout(() => showWarning(expiresAt), warningAt - now);
    }, [clearTimers, expireSession, idleTimeoutMinutes, showWarning]);

    const recordActivity = useCallback((force: boolean = false) =>
    {
        if (!userId || !idleTimeoutMinutes || warningOpenRef.current || expiredRef.current) return;
        const now = Date.now();
        if (!force && now - lastActivityRef.current < ACTIVITY_WRITE_THROTTLE_MS) return;

        lastActivityRef.current = now;
        localStorage.setItem(`${ACTIVITY_STORAGE_KEY_PREFIX}${userId}`, now.toString());
        scheduleFrom(now);

        if (!heartbeatInFlightRef.current && now - lastHeartbeatRef.current >= ACTIVITY_HEARTBEAT_THROTTLE_MS)
        {
            heartbeatInFlightRef.current = true;
            void onContinueRef.current()
                .catch(() => undefined)
                .finally(() =>
                {
                    heartbeatInFlightRef.current = false;
                    lastHeartbeatRef.current = Date.now();
                });
        }
    }, [idleTimeoutMinutes, scheduleFrom, userId]);

    useEffect(() =>
    {
        if (!userId || !idleTimeoutMinutes)
        {
            clearTimers();
            warningOpenRef.current = false;
            setIsWarningOpen(false);
            return;
        }

        expiredRef.current = false;
        lastHeartbeatRef.current = Date.now();
        recordActivity(true);
        const activityEvents: (keyof WindowEventMap)[] = ["keydown", "pointerdown", "scroll", "touchstart", "focus"];
        const handleActivity = () => recordActivity();
        const storageKey = `${ACTIVITY_STORAGE_KEY_PREFIX}${userId}`;
        const handleStorage = (event: StorageEvent) =>
        {
            if (event.key !== storageKey || !event.newValue) return;
            const activityAt = Number(event.newValue);
            if (!Number.isFinite(activityAt) || activityAt <= lastActivityRef.current) return;
            lastActivityRef.current = activityAt;
            scheduleFrom(activityAt);
        };

        activityEvents.forEach(eventName => window.addEventListener(eventName, handleActivity, {passive: true}));
        window.addEventListener("storage", handleStorage);

        return () =>
        {
            clearTimers();
            activityEvents.forEach(eventName => window.removeEventListener(eventName, handleActivity));
            window.removeEventListener("storage", handleStorage);
        };
    }, [clearTimers, idleTimeoutMinutes, recordActivity, scheduleFrom, userId]);

    const continueSession = useCallback(async () =>
    {
        if (isContinuing || expiredRef.current) return;
        setIsContinuing(true);
        setErrorMessage(null);
        try
        {
            await onContinueRef.current();
            warningOpenRef.current = false;
            setIsWarningOpen(false);
            recordActivity(true);
        }
        catch
        {
            setErrorMessage("We could not continue your session. Try again before the countdown ends.");
        }
        finally
        {
            setIsContinuing(false);
        }
    }, [isContinuing, recordActivity]);

    return {
        isWarningOpen,
        secondsRemaining,
        isContinuing,
        errorMessage,
        continueSession,
        expireSession,
    };
}
