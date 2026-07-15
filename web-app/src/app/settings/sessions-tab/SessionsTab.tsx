import React, {useEffect, useState} from "react";
import {
    Button,
    MessageBar,
    MessageBarBody,
    Spinner,
    Text,
} from "@fluentui/react-components";
import {UserSessionDto} from "../../models/models.tsx";
import {deleteUserSessionRecord, listUserSessions, revokeUserSession} from "../../../services/authApi.ts";
import {useSessionsTabStyles} from "./SessionsTabStyles.tsx";
import {realtimeService} from "../../../services/NotificationService.tsx";
import AllDeviceSignOutDialog from "../profile-tab/all-device-sign-out-dialog/AllDeviceSignOutDialog.tsx";
import {SignOutButtonIcon} from "../../components/IconBundles.tsx";
import SessionsTable from "./sessions-table/SessionsTable.tsx";

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

const SessionsTab: React.FC = () =>
{
    const styles = useSessionsTabStyles();
    const [sessions, setSessions] = useState<UserSessionDto[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [revoking, setRevoking] = useState<string | null>(null);
    const [deleting, setDeleting] = useState<string | null>(null);
    const [isAllDeviceSignOutDialogOpen, setIsAllDeviceSignOutDialogOpen] = useState(false);

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

    useEffect(() => { load(); }, []);

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

    const activeSessionsCount = sessions.filter(session => session.isActive).length;
    const endedSessionsCount = sessions.length - activeSessionsCount;

    return (
        <div
            className={styles.container}
            id={"device-sessions-container"}>
            <div
                id={"device-sessions-header"}
                className={styles.header}>
                <div
                    id={"device-sessions-summary"}
                    className={styles.summaryBlock}>
                    <Text
                        id={"device-sessions-title"}
                        size={600}
                        weight={"semibold"}>
                        Device sessions
                    </Text>
                    <Text
                        id={"device-sessions-count"}
                        size={200}
                        className={styles.subtleText}>
                        {activeSessionsCount} active {activeSessionsCount === 1 ? "session" : "sessions"}
                        {endedSessionsCount > 0 ? `, ${endedSessionsCount} ended` : ""}
                    </Text>
                </div>

                <Button
                    id={"button-sign-out-all-devices"}
                    appearance={"secondary"}
                    shape="circular"
                    icon={<SignOutButtonIcon/>}
                    onClick={() => setIsAllDeviceSignOutDialogOpen(true)}
                    size={"medium"}>
                    Sign out of all devices
                </Button>
            </div>

            <Text
                id={"device-sessions-intro"}
                size={200}
                className={styles.subtleText}>
                Active sessions stay at the top. Ended sessions remain visible as revoked or expired so you can review them and delete old records if you want.
            </Text>

            {error && (
                <MessageBar id={"device-sessions-error"} intent="error">
                    <MessageBarBody>{error}</MessageBarBody>
                </MessageBar>
            )}

            {loading && <Spinner id={"device-sessions-loading"} size="small" label="Loading sessions..."/>}

            {!loading && (
                <SessionsTable
                    sessions={sessions}
                    revoking={revoking}
                    deleting={deleting}
                    onRevoke={handleRevoke}
                    onDelete={handleDelete}
                />
            )}

            <AllDeviceSignOutDialog
                isOpen={isAllDeviceSignOutDialogOpen}
                onDismiss={() => setIsAllDeviceSignOutDialogOpen(false)}
            />
        </div>
    );
};

export default SessionsTab;
