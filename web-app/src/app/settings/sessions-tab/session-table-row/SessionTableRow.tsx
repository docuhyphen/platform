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
    isDeleting: boolean;
    onRevoke: () => void;
    onDelete: () => void;
}

const formatDate = (iso: string) =>
    new Date(iso).toLocaleString(undefined, {dateStyle: "medium", timeStyle: "short"});

const getDeviceName = (session: UserSessionDto) =>
    session.deviceName ?? session.userAgent?.split(' ')[0] ?? "Unknown device";

const getStatusBadge = (session: UserSessionDto) =>
{
    if (session.isCurrent && session.isActive)
    {
        return <Badge appearance="filled" color="brand">This device</Badge>;
    }

    if (session.isActive)
    {
        return <Badge appearance="outline" color="success">Active</Badge>;
    }

    if (session.revocationReasonCode === "EXCHANGE_EXPIRED")
    {
        return <Badge appearance="outline" color="warning">Expired</Badge>;
    }

    return <Badge appearance="tint" color="danger">Revoked</Badge>;
};

const getActionLabel = (session: UserSessionDto, isBusy: boolean) =>
{
    if (isBusy)
    {
        return <Spinner size="tiny"/>;
    }

    if (session.isActive)
    {
        return session.isCurrent ? "Sign out" : "Revoke";
    }

    return "Delete";
};

const SessionTableRow: React.FC<SessionTableRowProps> = (
    {
        session,
        isRevoking,
        isDeleting,
        onRevoke,
        onDelete,
    }
) =>
{
    const styles = useSessionsTabStyles();
    const isBusy = isRevoking || isDeleting;
    const lastChangedAt = session.revokedAt ?? session.expiresAt;

    return (
        <TableRow className={session.isCurrent ? styles.currentRow : undefined}>
            <TableCell className={styles.deviceColumn}>
                <div
                    id={`device-session-${session.sessionId}-device`}
                    className={styles.currentDeviceCell}>
                    <Text
                        size={300}
                        weight={"semibold"}
                        className={styles.deviceName}>
                        {getDeviceName(session)}
                    </Text>
                    {!session.isActive && lastChangedAt && (
                        <Text
                            id={`device-session-${session.sessionId}-ended-at`}
                            size={200}
                            className={styles.metaCell}>
                            Ended {formatDate(lastChangedAt)}
                        </Text>
                    )}
                </div>
            </TableCell>
            <TableCell className={styles.ipColumn}>
                <Text
                    size={200}
                    className={styles.metaCell}>
                    {session.ipAddress || "-"}
                </Text>
            </TableCell>
            <TableCell className={styles.dateColumn}>
                <Text
                    size={200}
                    className={styles.metaCell}>
                    {formatDate(session.lastSeenAt)}
                </Text>
            </TableCell>
            <TableCell className={styles.dateColumn}>
                <Text
                    size={200}
                    className={styles.metaCell}>
                    {formatDate(session.createdDate)}
                </Text>
            </TableCell>
            <TableCell className={styles.dateColumn}>
                <Text
                    size={200}
                    className={styles.metaCell}>
                    {session.expiresAt ? formatDate(session.expiresAt) : "-"}
                </Text>
            </TableCell>
            <TableCell className={styles.statusCell}>
                {getStatusBadge(session)}
            </TableCell>
            <TableCell className={styles.actionsCell}>
                <div id={`device-session-${session.sessionId}-action`}>
                    <Button
                        id={session.isActive
                            ? `button-revoke-session-${session.sessionId}`
                            : `button-delete-session-record-${session.sessionId}`}
                        icon={<DeleteRegular/>}
                        shape={"circular"}
                        appearance="secondary"
                        size="small"
                        disabled={isBusy}
                        onClick={session.isActive ? onRevoke : onDelete}>
                        {getActionLabel(session, isBusy)}
                    </Button>
                </div>
            </TableCell>
        </TableRow>
    );
};

export default SessionTableRow;
