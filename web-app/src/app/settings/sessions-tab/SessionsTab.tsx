import React, {useEffect, useState} from "react";
import {
    Badge,
    Button,
    Caption1,
    MessageBar,
    MessageBarBody,
    Spinner,
    Subtitle2,
    Text,
    Title3,
} from "@fluentui/react-components";
import {DeleteRegular} from "@fluentui/react-icons";
import {UserSessionDto} from "../../models/models.tsx";
import {listUserSessions, revokeUserSession} from "../../../services/authApi.ts";
import {useSessionsTabStyles} from "./SessionsTabStyles.tsx";
import {realtimeService} from "../../../services/NotificationService.tsx";
import PasswordResetDialog from "../profile-tab/password-reset-dialog/PasswordResetDialog.tsx";
import AllDeviceSignOutDialog from "../profile-tab/all-device-sign-out-dialog/AllDeviceSignOutDialog.tsx";

const formatDate = (iso: string) =>
    new Date(iso).toLocaleString(undefined, {dateStyle: "medium", timeStyle: "short"});

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
            // Surface the user's own device first,  both for quick recognition and so the
            // destructive "Sign out" action sits where they expect.
            const ordered = [...res.sessions].sort((a, b) =>
            {
                if (!!a.isCurrent !== !!b.isCurrent) return a.isCurrent ? -1 : 1;
                return new Date(b.lastSeenAt).getTime() - new Date(a.lastSeenAt).getTime();
            });
            setSessions(ordered);
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
            const newSession: UserSessionDto = {...msg.session, isCurrent: false};
            setSessions(prev =>
            {
                if (prev.some(s => s.sessionId === newSession.sessionId)) return prev;
                return [...prev, newSession].sort((a, b) =>
                {
                    if (!!a.isCurrent !== !!b.isCurrent) return a.isCurrent ? -1 : 1;
                    return new Date(b.lastSeenAt).getTime() - new Date(a.lastSeenAt).getTime();
                });
            });
        });

        const offRemoved = realtimeService.on('SESSION_REMOVED', (msg) =>
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

            <div className={styles.header}>
                <span></span>

                <div>
                    <Button appearance={"primary"}
                            shape="circular"
                            onClick={() => setIsAllDeviceSignOutDialogOpen(true)}
                            size={"medium"}> Sign out of all devices</Button>
                </div>
            </div>

            <Caption1>
                These are all devices currently signed in to your account. Revoking a session will sign that device out immediately.
            </Caption1>

            {error && (
                <MessageBar intent="error">
                    <MessageBarBody>{error}</MessageBarBody>
                </MessageBar>
            )}

            {loading && <Spinner size="small" label="Loading sessions…"/>}

            {!loading && sessions.length === 0 && (
                <Text>No active sessions found.</Text>
            )}
            <div className={styles.sessionCardContainer}>
                {!loading && sessions.map(session => (
                    <div key={session.sessionId} className={styles.sessionCard}>
                        <div className={styles.sessionMeta}>
                            <Subtitle2>
                                {session.deviceName ?? session.userAgent?.split(' ')[0] ?? "Unknown device"}
                            </Subtitle2>
                            {session.ipAddress && (
                                <Caption1>IP: {session.ipAddress}</Caption1>
                            )}
                            <Caption1>Last active: {formatDate(session.lastSeenAt)}</Caption1>
                            <Caption1>Created: {formatDate(session.createdDate)}</Caption1>
                            {session.expiresAt && (
                                <Caption1>Expires: {formatDate(session.expiresAt)}</Caption1>
                            )}
                        </div>
                        <div style={{display: 'flex', alignItems: 'center', gap: '8px'}}>
                            {session.isCurrent && (
                                <Badge appearance="filled" color="brand">
                                    This device
                                </Badge>
                            )}
                            <Button
                                icon={<DeleteRegular/>}
                                shape={"circular"}
                                appearance="subtle"
                                size="small"
                                disabled={revoking === session.sessionId}
                                onClick={() => handleRevoke(session.sessionId)}
                            >
                                {revoking === session.sessionId
                                    ? <Spinner size="tiny"/>
                                    : session.isCurrent ? "Sign out" : "Revoke"}
                            </Button>
                        </div>
                    </div>
                ))}
            </div>

            <AllDeviceSignOutDialog
                isOpen={isAllDeviceSignOutDialogOpen}
                onDismiss={() => setIsAllDeviceSignOutDialogOpen(false)}
            />
        </div>
    );
};

export default SessionsTab;
