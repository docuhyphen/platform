import {useEffect, useState} from "react";
import {UserSessionDto} from "../../models/models.tsx";
import {deleteUserSessionRecord, listUserSessions, revokeUserSession} from "../../../services/authApi.ts";
import {realtimeService} from "../../../services/NotificationService.tsx";

const sortSessions = (sessions: UserSessionDto[]) =>
    [...sessions].sort((a, b) =>
    {
        if (!!a.isCurrent !== !!b.isCurrent)
        {
            return a.isCurrent ? -1 : 1;
        }

        if (!!a.isActive !== !!b.isActive)
        {
            return a.isActive ? -1 : 1;
        }

        return new Date(b.lastSeenAt).getTime() - new Date(a.lastSeenAt).getTime();
    });

export const useUserSessions = () =>
{
    const [sessions, setSessions] = useState<UserSessionDto[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [revoking, setRevoking] = useState<string | null>(null);
    const [deleting, setDeleting] = useState<string | null>(null);

    const load = async () =>
    {
        setLoading(true);
        setError(null);
        try
        {
            const res = await listUserSessions();
            setSessions(sortSessions(res.sessions));
        }
        catch
        {
            setError("Failed to load sessions.");
        }
        finally
        {
            setLoading(false);
        }
    };

    useEffect(() =>
    {
        load();
    }, []);

    useEffect(() =>
    {
        const offCreated = realtimeService.on('SESSION_CREATED', (msg) =>
        {
            if (!msg.session) return;
            void load();
        });

        const offRemoved = realtimeService.on('SESSION_REMOVED', (msg) =>
        {
            if (!msg.userSessionId) return;
            void load();
        });

        return () =>
        {
            offCreated();
            offRemoved();
        };
    }, []);

    const handleRevoke = async (sessionId: string) =>
    {
        setRevoking(sessionId);
        try
        {
            await revokeUserSession(sessionId);
            await load();
        }
        catch
        {
            setError("Failed to revoke session. Please try again.");
        }
        finally
        {
            setRevoking(null);
        }
    };

    const handleDelete = async (sessionId: string) =>
    {
        setDeleting(sessionId);
        try
        {
            await deleteUserSessionRecord(sessionId);
            setSessions(prev => prev.filter(s => s.sessionId !== sessionId));
        }
        catch
        {
            setError("Failed to delete session record. Please try again.");
        }
        finally
        {
            setDeleting(null);
        }
    };

    return {sessions, loading, error, revoking, deleting, handleRevoke, handleDelete};
};
