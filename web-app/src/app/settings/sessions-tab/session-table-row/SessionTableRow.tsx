import React from "react";
import {TableCell, TableRow, Text} from "@fluentui/react-components";
import {UserSessionDto} from "../../../models/models.tsx";
import {formatSessionDate, getDeviceName} from "./sessionDisplay.ts";
import SessionStatusBadge from "./SessionStatusBadge.tsx";
import SessionActionButton from "./SessionActionButton.tsx";
import {useSessionTableRowStyles} from "./SessionTableRowStyles.tsx";

interface SessionTableRowProps
{
    session: UserSessionDto;
    isRevoking: boolean;
    isDeleting: boolean;
    onRevoke: () => void;
    onDelete: () => void;
}

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
    const styles = useSessionTableRowStyles();
    const isBusy = isRevoking || isDeleting;
    const lastChangedAt = session.revokedAt ?? session.expiresAt;

    return (
        <TableRow id={"session-table-row-table-row-1"}
                  className={session.isCurrent ? styles.currentRow : undefined}>
            <TableCell id={"session-table-row-table-cell-1"}
                       className={styles.deviceColumn}>
                <div
                    id={`device-session-${session.sessionId}-device`}
                    className={styles.currentDeviceCell}>
                    <Text
                        id={"session-table-row-text-1"}
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
                            Ended {formatSessionDate(lastChangedAt)}
                        </Text>
                    )}
                </div>
            </TableCell>
            <TableCell id={"session-table-row-table-cell-2"}
                       className={styles.ipColumn}>
                <Text
                    id={"session-table-row-text-2"}
                    size={200}
                    className={styles.metaCell}>
                    {session.ipAddress || "-"}
                </Text>
            </TableCell>
            <TableCell id={"session-table-row-table-cell-3"}
                       className={styles.dateColumn}>
                <Text
                    id={"session-table-row-text-3"}
                    size={200}
                    className={styles.metaCell}>
                    {formatSessionDate(session.lastSeenAt)}
                </Text>
            </TableCell>
            <TableCell id={"session-table-row-table-cell-4"}
                       className={styles.dateColumn}>
                <Text
                    id={"session-table-row-text-4"}
                    size={200}
                    className={styles.metaCell}>
                    {formatSessionDate(session.createdDate)}
                </Text>
            </TableCell>
            <TableCell id={"session-table-row-table-cell-5"}
                       className={styles.dateColumn}>
                <Text
                    id={"session-table-row-text-5"}
                    size={200}
                    className={styles.metaCell}>
                    {session.expiresAt ? formatSessionDate(session.expiresAt) : "-"}
                </Text>
            </TableCell>
            <TableCell id={"session-table-row-table-cell-6"}
                       className={styles.statusCell}>
                <SessionStatusBadge session={session}/>
            </TableCell>
            <TableCell id={"session-table-row-table-cell-7"}
                       className={styles.actionsCell}>
                <SessionActionButton session={session}
                                     isBusy={isBusy}
                                     onRevoke={onRevoke}
                                     onDelete={onDelete}/>
            </TableCell>
        </TableRow>
    );
};

export default SessionTableRow;
