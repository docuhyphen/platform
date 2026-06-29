import React from "react";
import {
    Badge,
    Button,
    Spinner,
    TableCell,
    TableRow,
    Text,
} from "@fluentui/react-components";
import {DeleteRegular} from "@fluentui/react-icons";
import {UserSessionDto} from "../../../models/models.tsx";
import {useSessionsTabStyles} from "../SessionsTabStyles.tsx";

interface SessionTableRowProps
{
    session: UserSessionDto;
    isRevoking: boolean;
    onRevoke: () => void;
}

const formatDate = (iso: string) =>
    new Date(iso).toLocaleString(undefined, {dateStyle: "medium", timeStyle: "short"});

const getDeviceName = (session: UserSessionDto) =>
    session.deviceName ?? session.userAgent?.split(' ')[0] ?? "Unknown device";

const SessionTableRow: React.FC<SessionTableRowProps> = (
    {
        session,
        isRevoking,
        onRevoke
    }
) =>
{
    const styles = useSessionsTabStyles();

    return (
        <TableRow className={session.isCurrent ? styles.currentRow : undefined}>
            <TableCell className={styles.deviceColumn}>
                <div id={`device-session-${session.sessionId}-device`} className={styles.currentDeviceCell}>
                    <Text size={300} weight={"semibold"} className={styles.deviceName}>
                        {getDeviceName(session)}
                    </Text>
                </div>
            </TableCell>
            <TableCell className={styles.ipColumn}><Text size={200} className={styles.metaCell}>{session.ipAddress || "-"}</Text></TableCell>
            <TableCell className={styles.dateColumn}><Text size={200} className={styles.metaCell}>{formatDate(session.lastSeenAt)}</Text></TableCell>
            <TableCell className={styles.dateColumn}><Text size={200} className={styles.metaCell}>{formatDate(session.createdDate)}</Text></TableCell>
            <TableCell className={styles.dateColumn}><Text size={200} className={styles.metaCell}>{session.expiresAt ? formatDate(session.expiresAt) : "-"}</Text></TableCell>
            <TableCell className={styles.statusCell}>
                {session.isCurrent
                    ? <Badge appearance="filled" color="brand">This device</Badge>
                    : <Badge appearance="outline" color="success">Active</Badge>}
            </TableCell>
            <TableCell>
                <div id={`device-session-${session.sessionId}-action`}>
                    <Button
                        id={`button-revoke-session-${session.sessionId}`}
                        icon={<DeleteRegular/>}
                        shape={"circular"}
                        appearance="secondary"
                        size="small"
                        disabled={isRevoking}
                        onClick={onRevoke}>
                        {isRevoking ? <Spinner size="tiny"/> : session.isCurrent ? "Sign out" : "Revoke"}
                    </Button>
                </div>
            </TableCell>
        </TableRow>
    );
};

export default SessionTableRow;
