import {useCallback, useEffect, useRef, useState} from "react";
import {useIdleTimer} from "react-idle-timer";
import type {EventsType} from "react-idle-timer";
import type {CurrentSessionDto} from "../../models/models.tsx";

const WARNING_DURATION_MS = 60_000;
const ACTIVITY_HEARTBEAT_THROTTLE_MS = 15_000;
const ACTIVITY_EVENT_THROTTLE_MS = 1_000;
const ACTIVITY_DETECTION_TIMEOUT_MS = 2_147_483_646;
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
    "focus",
    "focusin",
];

export type SessionExpiryReason = "INACTIVITY_TIMEOUT" | "SESSION_EXPIRED";

interface SessionDeadlines
{
    idleExpiresAt: number;
    sessionExpiresAt: number | null;
}

interface UseSessionInactivityOptions
{
    currentSession: CurrentSessionDto | null;
    onContinue: () => Promise<CurrentSessionDto>;
    onExpire: (reason: SessionExpiryReason) => Promise<void>;
}

interface UseSessionInactivityResult
{
    isWarningOpen: boolean;
    warningReason: SessionExpiryReason;
    secondsRemaining: number;
    isContinuing: boolean;
    errorMessage: string | null;
    continueSession: () => Promise<void>;
    expireSession: () => Promise<void>;
}

function toLocalDeadlines(session: CurrentSessionDto): SessionDeadlines
{
    const localNow = Date.now();
    const serverOffset = localNow - session.serverTimeEpochMs;
    return {
        idleExpiresAt: session.idleExpiresAtEpochMs + serverOffset,
        sessionExpiresAt: session.sessionExpiresAtEpochMs === null
            ? null
            : session.sessionExpiresAtEpochMs + serverOffset,
    };
}

function nextExpiry(deadlines: SessionDeadlines): {expiresAt: number; reason: SessionExpiryReason}
{
    if (deadlines.sessionExpiresAt !== null && deadlines.sessionExpiresAt <= deadlines.idleExpiresAt)
    {
        return {expiresAt: deadlines.sessionExpiresAt, reason: "SESSION_EXPIRED"};
    }
    return {expiresAt: deadlines.idleExpiresAt, reason: "INACTIVITY_TIMEOUT"};
}

export function useSessionInactivity(
    {
        currentSession,
        onContinue,
        onExpire,
    }: UseSessionInactivityOptions
): UseSessionInactivityResult
{
    const enabled = currentSession !== null && currentSession.idleTimeoutMinutes > 0;
    const [deadlines, setDeadlines] = useState<SessionDeadlines | null>(() =>
        currentSession ? toLocalDeadlines(currentSession) : null);
    const [isWarningOpen, setIsWarningOpen] = useState(false);
    const [warningReason, setWarningReason] = useState<SessionExpiryReason>("INACTIVITY_TIMEOUT");
    const [secondsRemaining, setSecondsRemaining] = useState(60);
    const [isContinuing, setIsContinuing] = useState(false);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);
    const expiredRef = useRef(false);
    const warningReasonRef = useRef<SessionExpiryReason>("INACTIVITY_TIMEOUT");
    const lastHeartbeatRef = useRef(0);
    const heartbeatInFlightRef = useRef(false);
    const onContinueRef = useRef(onContinue);
    const onExpireRef = useRef(onExpire);

    useEffect(() =>
    {
        onContinueRef.current = onContinue;
        onExpireRef.current = onExpire;
    }, [onContinue, onExpire]);

    const applySessionTiming = useCallback((session: CurrentSessionDto) =>
    {
        setDeadlines(toLocalDeadlines(session));
        setErrorMessage(null);
    }, []);

    const expireWithReason = useCallback(async (reason: SessionExpiryReason) =>
    {
        if (expiredRef.current) return;
        expiredRef.current = true;
        setIsWarningOpen(false);
        await onExpireRef.current(reason);
    }, []);

    const expireSession = useCallback(async () =>
    {
        await expireWithReason(warningReasonRef.current);
    }, [expireWithReason]);

    const handleActivity = useCallback(() =>
    {
        if (!enabled || expiredRef.current || heartbeatInFlightRef.current) return;

        const now = Date.now();
        if (now - lastHeartbeatRef.current < ACTIVITY_HEARTBEAT_THROTTLE_MS) return;

        heartbeatInFlightRef.current = true;
        lastHeartbeatRef.current = now;
        void onContinueRef.current()
            .then(applySessionTiming)
            .catch(() =>
            {
                lastHeartbeatRef.current = 0;
            })
            .finally(() =>
            {
                heartbeatInFlightRef.current = false;
            });
    }, [applySessionTiming, enabled]);

    useIdleTimer({
        timeout: ACTIVITY_DETECTION_TIMEOUT_MS,
        events: ACTIVITY_EVENTS,
        eventsThrottle: ACTIVITY_EVENT_THROTTLE_MS,
        disabled: !enabled,
        crossTab: true,
        syncTimers: ACTIVITY_EVENT_THROTTLE_MS,
        name: `docuhyphen-session-${currentSession?.userId ?? "anonymous"}`,
        onAction: handleActivity,
    });

    useEffect(() =>
    {
        expiredRef.current = false;
        lastHeartbeatRef.current = 0;
        heartbeatInFlightRef.current = false;
        setDeadlines(currentSession ? toLocalDeadlines(currentSession) : null);
        setIsWarningOpen(false);
        setErrorMessage(null);
        setSecondsRemaining(60);
    }, [currentSession]);

    useEffect(() =>
    {
        if (!enabled || deadlines === null) return;

        const expiry = nextExpiry(deadlines);
        const openWarning = () =>
        {
            warningReasonRef.current = expiry.reason;
            setWarningReason(expiry.reason);
            setIsWarningOpen(true);
            setErrorMessage(null);
        };
        const warningDelay = expiry.expiresAt - WARNING_DURATION_MS - Date.now();
        const expiryDelay = expiry.expiresAt - Date.now();

        if (warningDelay <= 0) openWarning();
        else setIsWarningOpen(false);

        const warningTimer = warningDelay > 0
            ? window.setTimeout(openWarning, warningDelay)
            : null;
        const expiryTimer = window.setTimeout(
            () => void expireWithReason(expiry.reason),
            Math.max(0, expiryDelay),
        );

        return () =>
        {
            if (warningTimer !== null) window.clearTimeout(warningTimer);
            window.clearTimeout(expiryTimer);
        };
    }, [deadlines, enabled, expireWithReason]);

    useEffect(() =>
    {
        if (!isWarningOpen || deadlines === null) return;
        const updateCountdown = () =>
        {
            const expiry = nextExpiry(deadlines);
            setSecondsRemaining(Math.max(0, Math.ceil((expiry.expiresAt - Date.now()) / 1_000)));
        };

        updateCountdown();
        const countdownTimer = window.setInterval(updateCountdown, 1_000);
        return () => window.clearInterval(countdownTimer);
    }, [deadlines, isWarningOpen]);

    const continueSession = useCallback(async () =>
    {
        if (isContinuing || expiredRef.current || warningReasonRef.current === "SESSION_EXPIRED") return;
        setIsContinuing(true);
        setErrorMessage(null);
        try
        {
            const session = await onContinueRef.current();
            expiredRef.current = false;
            applySessionTiming(session);
            setIsWarningOpen(false);
            setSecondsRemaining(60);
            lastHeartbeatRef.current = Date.now();
        }
        catch
        {
            setErrorMessage("We could not continue your session. Try again before the countdown ends.");
        }
        finally
        {
            setIsContinuing(false);
        }
    }, [applySessionTiming, isContinuing]);

    return {
        isWarningOpen,
        warningReason,
        secondsRemaining,
        isContinuing,
        errorMessage,
        continueSession,
        expireSession,
    };
}
