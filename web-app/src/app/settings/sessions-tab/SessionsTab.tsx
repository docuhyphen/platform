import React, {useEffect, useState} from "react";
import {
    Button,
    MessageBar,
    MessageBarBody,
    Spinner,
    Text,
} from "@fluentui/react-components";
import {UserSessionDto} from "../../models/models.tsx";
import {listUserSessions, revokeUserSession} from "../../../services/authApi.ts";
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

        return new Date(b.lastSeenAt).getTime() - new Date(a.lastSeenAt).getTime();
    });

const SessionsTab: React.FC = () =>
{
    const styles = useSessionsTabStyles();
    const [sessions, setSessions] = useState<UserSessionDto[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [revoking, setRevoking] = useState<string | null>(null);
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
        const offCreated = realtimeService.on('EXCHANGE_CREATED', (msg) =>
        {
            if (!msg.session) return;

            const newSession: UserSessionDto = {...msg.session, isCurrent: false};
            setSessions(prev =>
            {
                if (prev.some(s => s.sessionId === newSession.sessionId)) return prev;
                return sortSessions([...prev, newSession]);
            });
        });

        const offRemoved = realtimeService.on('EXCHANGE_REMOVED', (msg) =>
        {
            if (!msg.userSessionId) return;
            setSessions(prev => prev.filter(s => s.sessionId !== msg.userSessionId));
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
            setSessions(prev => prev.filter(s => s.sessionId !== sessionId));
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

    return (
        <div className={styles.container} id={"device-sessions-container"}>
            <div id={"device-sessions-header"} className={styles.header}>
                <div id={"device-sessions-summary"} className={styles.summaryBlock}>
                    <Text id={"device-sessions-title"} size={600} weight={"semibold"}>Device sessions</Text>
                    <Text id={"device-sessions-count"} size={200} className={styles.subtleText}>
                        {sessions.length} active {sessions.length === 1 ? "session" : "sessions"}
                    </Text>
                </div>

                <Button id={"button-sign-out-all-devices"}
                        appearance={"secondary"}
                        shape="circular"
                        icon={<SignOutButtonIcon/>}
                        onClick={() => setIsAllDeviceSignOutDialogOpen(true)}
                        size={"medium"}>
                    Sign out of all devices
                </Button>
            </div>

            <Text id={"device-sessions-intro"} size={200} className={styles.subtleText}>
                These are all devices currently signed in to your account. Revoking a session will sign that device out immediately.
            </Text>

            {error && (
                <MessageBar id={"device-sessions-error"} intent="error">
                    <MessageBarBody>{error}</MessageBarBody>
                </MessageBar>
            )}

            {loading && <Spinner id={"device-sessions-loading"} size="small" label="Loading sessions..."/>}

            {!loading && <SessionsTable sessions={sessions} revoking={revoking} onRevoke={handleRevoke}/>}

            <AllDeviceSignOutDialog
                isOpen={isAllDeviceSignOutDialogOpen}
                onDismiss={() => setIsAllDeviceSignOutDialogOpen(false)}
            />
        </div>
    );
};

export default SessionsTab;
