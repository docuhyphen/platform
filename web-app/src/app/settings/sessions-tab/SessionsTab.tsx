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

const formatDate = (iso: string) =>
    new Date(iso).toLocaleString(undefined, {dateStyle: "medium", timeStyle: "short"});

const SessionsTab: React.FC = () =>
{
    const styles = useSessionsTabStyles();
    const [sessions, setSessions] = useState<UserSessionDto[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [revoking, setRevoking] = useState<string | null>(null);

    const load = async () =>
    {
        setLoading(true);
        setError(null);
        try
        {
            const res = await listUserSessions();
            setSessions(res.sessions);
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
        <div className={styles.container}>
            <div className={styles.header}>
                <Title3>Active Sessions</Title3>
                <Button appearance="subtle" size="small" onClick={load} disabled={loading}>
                    Refresh
                </Button>
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
                        <Badge appearance="outline" color="informative" size="small">Active</Badge>
                        <Button
                            icon={<DeleteRegular/>}
                            appearance="subtle"
                            size="small"
                            disabled={revoking === session.sessionId}
                            onClick={() => handleRevoke(session.sessionId)}
                        >
                            {revoking === session.sessionId ? <Spinner size="tiny"/> : "Revoke"}
                        </Button>
                    </div>
                </div>
            ))}
        </div>
    );
};

export default SessionsTab;
