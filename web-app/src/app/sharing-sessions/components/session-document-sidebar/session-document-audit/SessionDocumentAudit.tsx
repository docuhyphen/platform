import React, {useEffect, useState} from "react";
import {
    Spinner,
    Table,
    TableBody,
    TableCell,
    TableHeader,
    TableHeaderCell,
    TableRow,
    Text
} from "@fluentui/react-components";
import {useSessionDocumentAuditStyles} from "./SessionDocumentAuditStyles";
import {DocumentAuditDetailedDto, DocumentDetailedDto} from "../../../../models/models";
import {fetchSharingSessionDocumentAuditLogs} from "../../../../../services/sharingSessionApi";
import {formatAuditAction, formatDate} from "../../../../helpers.ts";

interface SessionDocumentAuditProps
{
    sessionId: string;
    sessionDocument: DocumentDetailedDto;
}

const SessionDocumentAudit: React.FC<SessionDocumentAuditProps> = ({
                                                                       sessionId,
                                                                       sessionDocument
                                                                   }) =>
{
    const [auditLogs, setAuditLogs] = useState<DocumentAuditDetailedDto[]>([]);
    const [loading, setLoading] = useState<boolean>(true);
    const [error, setError] = useState<string | null>(null);
    const styles = useSessionDocumentAuditStyles();

    useEffect(() =>
    {
        const fetchAuditLogs = async () =>
        {
            try
            {
                setLoading(true);
                const logs = await fetchSharingSessionDocumentAuditLogs(sessionId, sessionDocument.id);
                setAuditLogs(logs as DocumentAuditDetailedDto[]);
                setError(null);
            }
            catch (err: any)
            {
                setError(err.message || "Failed to load audit logs");
                console.error("Error fetching audit logs:", err);
            }
            finally
            {
                setLoading(false);
            }
        };

        fetchAuditLogs();
    }, [sessionId, sessionDocument.id]);

    if (loading)
    {
        return <Spinner size={"small"}/>;
    }

    if (error)
    {
        return <Text className={styles.error}>Error: {error}</Text>;
    }

    if (!auditLogs || auditLogs.length === 0)
    {
        return <Text className={styles.noLogs}>
            No audit logs available for this document.
        </Text>;
    }

    return (
        <div className={styles.auditContainer}>
            <Table className={styles.auditTable}>
                <TableHeader>
                    <TableRow>
                        <TableHeaderCell>Date & Time</TableHeaderCell>
                        <TableHeaderCell>Action</TableHeaderCell>
                        <TableHeaderCell>User</TableHeaderCell>
                    </TableRow>
                </TableHeader>
                <TableBody>
                    {auditLogs.map((log) => (
                        <TableRow key={log.id}>
                            <TableCell>{formatDate(log.timestamp)}</TableCell>
                            <TableCell>{formatAuditAction(log.action)}</TableCell>
                            <TableCell>{log.performedByEmail || 'Unknown'}</TableCell>
                        </TableRow>
                    ))}
                </TableBody>
            </Table>
        </div>
    );
};

export default SessionDocumentAudit;