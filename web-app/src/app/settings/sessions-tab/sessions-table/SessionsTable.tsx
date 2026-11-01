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
import {useSessionsTableStyles} from "./SessionsTableStyles.tsx";
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
    const styles = useSessionsTableStyles();

    return (
        <div
            id={"device-sessions-table-wrapper"}
            className={styles.tableWrapper}>
            <Table
                id={"device-sessions-table"}
                className={styles.table}>
                <TableHeader id={"sessions-table-table-header-1"}>
                    <TableRow id={"sessions-table-table-row-1"}>
                        <TableHeaderCell id={"sessions-table-table-header-cell-1"}
                                         className={styles.deviceColumn}>Device</TableHeaderCell>
                        <TableHeaderCell id={"sessions-table-table-header-cell-2"}
                                         className={styles.ipColumn}>IP Address</TableHeaderCell>
                        <TableHeaderCell id={"sessions-table-table-header-cell-3"}
                                         className={styles.dateColumn}>Last Active</TableHeaderCell>
                        <TableHeaderCell id={"sessions-table-table-header-cell-4"}
                                         className={styles.dateColumn}>Created</TableHeaderCell>
                        <TableHeaderCell id={"sessions-table-table-header-cell-5"}
                                         className={styles.dateColumn}>Expires</TableHeaderCell>
                        <TableHeaderCell id={"sessions-table-table-header-cell-6"}
                                         className={styles.statusColumn}>Status</TableHeaderCell>
                        <TableHeaderCell id={"sessions-table-table-header-cell-7"}
                                         className={styles.actionsColumn}></TableHeaderCell>
                    </TableRow>
                </TableHeader>
                <TableBody id={"sessions-table-table-body-1"}>
                    {sessions.length === 0 && (
                        <TableRow id={"sessions-table-table-row-2"}>
                            <TableCell id={"sessions-table-table-cell-1"}
                                       colSpan={7}>
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
