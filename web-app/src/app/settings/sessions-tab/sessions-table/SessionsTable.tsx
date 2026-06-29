import React from "react";
import {
    Table,
    TableBody,
    TableCell,
    TableHeader,
    TableHeaderCell,
    TableRow,
    Text,
} from "@fluentui/react-components";
import {UserSessionDto} from "../../../models/models.tsx";
import {useSessionsTabStyles} from "../SessionsTabStyles.tsx";
import SessionTableRow from "../session-table-row/SessionTableRow.tsx";

interface SessionsTableProps
{
    sessions: UserSessionDto[];
    revoking: string | null;
    deleting: string | null;
    onRevoke: (sessionId: string) => void;
    onDelete: (sessionId: string) => void;
}

const SessionsTable: React.FC<SessionsTableProps> = (
    {
        sessions,
        revoking,
        deleting,
        onRevoke,
        onDelete,
    }
) =>
{
    const styles = useSessionsTabStyles();

    return (
        <div
            id={"device-sessions-table-wrapper"}
            className={styles.tableWrapper}>
            <Table
                id={"device-sessions-table"}
                className={styles.table}>
                <TableHeader>
                    <TableRow>
                        <TableHeaderCell className={styles.deviceColumn}>Device</TableHeaderCell>
                        <TableHeaderCell className={styles.ipColumn}>IP Address</TableHeaderCell>
                        <TableHeaderCell className={styles.dateColumn}>Last Active</TableHeaderCell>
                        <TableHeaderCell className={styles.dateColumn}>Created</TableHeaderCell>
                        <TableHeaderCell className={styles.dateColumn}>Expires</TableHeaderCell>
                        <TableHeaderCell className={styles.statusColumn}>Status</TableHeaderCell>
                        <TableHeaderCell className={styles.actionsColumn}></TableHeaderCell>
                    </TableRow>
                </TableHeader>
                <TableBody>
                    {sessions.length === 0 && (
                        <TableRow>
                            <TableCell colSpan={7}>
                                <Text id={"device-sessions-empty"}>No session records found.</Text>
                            </TableCell>
                        </TableRow>
                    )}
                    {sessions.map((session) => (
                        <SessionTableRow
                            key={session.sessionId}
                            session={session}
                            isRevoking={revoking === session.sessionId}
                            isDeleting={deleting === session.sessionId}
                            onRevoke={() => onRevoke(session.sessionId)}
                            onDelete={() => onDelete(session.sessionId)}
                        />
                    ))}
                </TableBody>
            </Table>
        </div>
    );
};

export default SessionsTable;
