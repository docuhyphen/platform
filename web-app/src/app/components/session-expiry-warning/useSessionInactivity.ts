import {useCallback, useEffect, useRef, useState} from "react";
import {useIdleTimer} from "react-idle-timer";
import type {EventsType} from "react-idle-timer";

const WARNING_DURATION_MS = 60_000;
const ACTIVITY_HEARTBEAT_THROTTLE_MS = 15_000;
const ACTIVITY_EVENT_THROTTLE_MS = 1_000;
const ACTIVITY_EVENTS: EventsType[] = [
    "keydown",
    "input",
    "pointerdown",
    "pointermove",
    "mousemove",
    "wheel",
    "scroll",
    "touchstart",
    "touchmove",
    "focusin",
    "visibilitychange",
];

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
    const configuredTimeout = idleTimeoutMinutes ? idleTimeoutMinutes * 60_000 : 0;
    const enabled = Boolean(userId) && configuredTimeout > 0;
    const timeout = enabled ? configuredTimeout : WARNING_DURATION_MS;
    const promptBeforeIdle = Math.min(WARNING_DURATION_MS, Math.max(0, timeout - 1));
    const [isWarningOpen, setIsWarningOpen] = useState(false);
    const [secondsRemaining, setSecondsRemaining] = useState(60);
    const [isContinuing, setIsContinuing] = useState(false);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);
    const expiredRef = useRef(false);
    const lastHeartbeatRef = useRef(Date.now());
    const heartbeatInFlightRef = useRef(false);
    const onContinueRef = useRef(onContinue);
    const onExpireRef = useRef(onExpire);

    useEffect(() =>
    {
        onContinueRef.current = onContinue;
        onExpireRef.current = onExpire;
    }, [onContinue, onExpire]);

    const expireSession = useCallback(async () =>
    {
        if (expiredRef.current) return;
        expiredRef.current = true;
        setIsWarningOpen(false);
        await onExpireRef.current();
    }, []);

    const handleActivity = useCallback(() =>
    {
        if (!enabled || expiredRef.current || heartbeatInFlightRef.current) return;

        const now = Date.now();
        if (now - lastHeartbeatRef.current < ACTIVITY_HEARTBEAT_THROTTLE_MS) return;

        heartbeatInFlightRef.current = true;
        void onContinueRef.current()
            .catch(() => undefined)
            .finally(() =>
            {
                heartbeatInFlightRef.current = false;
                lastHeartbeatRef.current = Date.now();
            });
    }, [enabled]);

    const idleTimer = useIdleTimer({
        timeout,
        promptBeforeIdle,
        events: ACTIVITY_EVENTS,
        eventsThrottle: ACTIVITY_EVENT_THROTTLE_MS,
        disabled: !enabled,
        crossTab: true,
        syncTimers: ACTIVITY_EVENT_THROTTLE_MS,
        name: `docuhyphen-session-${userId ?? "anonymous"}`,
        onAction: handleActivity,
        onPrompt: () =>
        {
            setIsWarningOpen(true);
            setErrorMessage(null);
        },
        onActive: () =>
        {
            setIsWarningOpen(false);
            setErrorMessage(null);
        },
        onIdle: () => void expireSession(),
    });

    useEffect(() =>
    {
        expiredRef.current = false;
        lastHeartbeatRef.current = Date.now();
        heartbeatInFlightRef.current = false;
        setIsWarningOpen(false);
        setErrorMessage(null);
        setSecondsRemaining(60);
    }, [enabled, idleTimeoutMinutes, userId]);

    useEffect(() =>
    {
        if (!isWarningOpen) return;

        const updateCountdown = () =>
        {
            setSecondsRemaining(Math.max(0, Math.ceil(idleTimer.getRemainingTime() / 1_000)));
        };

        updateCountdown();
        const countdownTimer = window.setInterval(updateCountdown, 1_000);
        return () => window.clearInterval(countdownTimer);
    }, [idleTimer, isWarningOpen]);

    const continueSession = useCallback(async () =>
    {
        if (isContinuing || expiredRef.current) return;
        setIsContinuing(true);
        setErrorMessage(null);
        try
        {
            await onContinueRef.current();
            expiredRef.current = false;
            idleTimer.activate();
            setIsWarningOpen(false);
            setSecondsRemaining(60);
        }
        catch
        {
            setErrorMessage("We could not continue your session. Try again before the countdown ends.");
        }
        finally
        {
            setIsContinuing(false);
        }
    }, [idleTimer, isContinuing]);

    return {
        isWarningOpen,
        secondsRemaining,
        isContinuing,
        errorMessage,
        continueSession,
        expireSession,
    };
}
