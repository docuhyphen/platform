import React, {useCallback} from "react";
import {useNavigate} from "react-router-dom";
import {fetchCurrentSession} from "../../../services/appUserApi.ts";
import {signOut} from "../../../services/authApi.ts";
import {realtimeService} from "../../../services/NotificationService.tsx";
import {CurrentSessionDto} from "../../models/models.tsx";
import SessionExpiryWarningDialog from "./SessionExpiryWarningDialog.tsx";
import {useSessionInactivity} from "./useSessionInactivity.ts";

interface SessionInactivityGuardProps
{
    token: string | null;
    currentSession: CurrentSessionDto | null;
    setToken: (token: string | null) => void;
}

const SessionInactivityGuard: React.FC<SessionInactivityGuardProps> = (
    {
        token,
        currentSession,
        setToken,
    }
) =>
{
    const navigate = useNavigate();

    const continueSession = useCallback(async () =>
    {
        await fetchCurrentSession();
    }, []);

    const endServerSession = useCallback(async () =>
    {
        realtimeService.disconnect();
        if (!token) return;

        try
        {
            await signOut(false, token);
        }
        catch
        {
            // Local sign-out still proceeds when the server has already ended the session.
        }
    }, [token]);

    const expireSession = useCallback(async () =>
    {
        await endServerSession();
        window.dispatchEvent(new CustomEvent("auth-session-expired", {
            detail: {reason: "INACTIVITY_TIMEOUT"},
        }));
    }, [endServerSession]);

    const signOutNow = useCallback(async () =>
    {
        await endServerSession();
        setToken(null);
        navigate("/sign-in");
    }, [endServerSession, navigate, setToken]);

    const sessionInactivity = useSessionInactivity({
        userId: currentSession?.userId ?? null,
        idleTimeoutMinutes: currentSession?.idleTimeoutMinutes ?? null,
        onContinue: continueSession,
        onExpire: expireSession,
    });

    return (
        <SessionExpiryWarningDialog
            isOpen={sessionInactivity.isWarningOpen}
            secondsRemaining={sessionInactivity.secondsRemaining}
            isContinuing={sessionInactivity.isContinuing}
            errorMessage={sessionInactivity.errorMessage}
            onContinue={() => void sessionInactivity.continueSession()}
            onSignOut={() => void signOutNow()}/>
    );
};

export default SessionInactivityGuard;
