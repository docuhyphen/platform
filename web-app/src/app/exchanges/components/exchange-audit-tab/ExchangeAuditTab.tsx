import React, {useEffect, useState} from "react";
import {
    Caption1,
    Spinner,
    Table,
    TableBody,
    TableCell,
    TableHeader,
    TableHeaderCell,
    TableRow,
    Text,
} from "@fluentui/react-components";
import {DocumentAuditDetailedDto, DocumentDetailedDto, ExchangeDetailedDto} from "../../../models/models.tsx";
import {fetchExchangeDocumentAuditLogs} from "../../../../services/exchangeApi.ts";
import {formatAuditAction, formatDate} from "../../../helpers.ts";
import {useExchangeAuditTabStyles} from "./ExchangeAuditTabStyles.tsx";

interface AuditEntry extends DocumentAuditDetailedDto
{
    documentTitle?: string;
    documentId?: string;
}

interface ExchangeAuditTabProps
{
    exchange: ExchangeDetailedDto;
}

const ExchangeAuditTab: React.FC<ExchangeAuditTabProps> = ({exchange}) =>
{
    const styles = useExchangeAuditTabStyles();
    const [auditEntries, setAuditEntries] = useState<AuditEntry[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() =>
    {
        if (!exchange?.id || !exchange.documents?.length)
        {
            setLoading(false);
            return;
        }

        const fetchAll = async () =>
        {
            setLoading(true);
            setError(null);

            try
            {
                const allDocuments: DocumentDetailedDto[] = exchange.documents ?? [];

                const results = await Promise.allSettled(
                    allDocuments.map(async (doc) =>
                    {
                        const logs = (await fetchExchangeDocumentAuditLogs(exchange.id, doc.id!)) as DocumentAuditDetailedDto[];
                        return logs.map((log): AuditEntry => ({
                            ...log,
                            documentTitle: doc.title,
                            documentId: doc.id,
                        }));
                    })
                );

                const merged: AuditEntry[] = [];
                for (const result of results)
                {
                    if (result.status === "fulfilled")
                    {
                        merged.push(...result.value);
                    }
                }

                // Sort by timestamp descending (most recent first).
                merged.sort((a, b) =>
                {
                    const ta = a.timestamp ? new Date(a.timestamp).getTime() : 0;
                    const tb = b.timestamp ? new Date(b.timestamp).getTime() : 0;
                    return tb - ta;
                });

                setAuditEntries(merged);
            }
            catch (err: unknown)
            {
                const message = err instanceof Error ? err.message : "Failed to load audit logs.";
                setError(message);
                console.error("ExchangeAuditTab: failed to load audit logs", err);
            }
            finally
            {
                setLoading(false);
            }
        };

        fetchAll();
    }, [exchange?.id, exchange?.documents?.length]);

    if (loading)
    {
        return (
            <div className={styles.spinner}>
                <Spinner size="small" label="Loading audit logs..." labelPosition="after"/>
            </div>
        );
    }

    if (error)
    {
        return <Text className={styles.errorText}>Error: {error}</Text>;
    }

    if (auditEntries.length === 0)
    {
        return (
            <Text className={styles.emptyText}>
                No audit logs found for this exchange.
            </Text>
        );
    }

    return (
        <div className={styles.container}>
            <Table className={styles.table}>
                <TableHeader>
                    <TableRow>
                        <TableHeaderCell>Date &amp; Time</TableHeaderCell>
                        <TableHeaderCell>Document</TableHeaderCell>
                        <TableHeaderCell>Action</TableHeaderCell>
                        <TableHeaderCell>User</TableHeaderCell>
                    </TableRow>
                </TableHeader>
                <TableBody>
                    {auditEntries.map((entry, index) => (
                        <TableRow key={entry.id ?? `${entry.documentId}-${index}`}>
                            <TableCell>
                                <Caption1>{entry.timestamp ? formatDate(entry.timestamp) : "-"}</Caption1>
                            </TableCell>
                            <TableCell>
                                <Caption1 className={styles.docLabel}>{entry.documentTitle ?? "-"}</Caption1>
                            </TableCell>
                            <TableCell>
                                <Caption1>{entry.action ? formatAuditAction(entry.action) : "-"}</Caption1>
                            </TableCell>
                            <TableCell>
                                <Caption1>{entry.performedByEmail ?? "-"}</Caption1>
                            </TableCell>
                        </TableRow>
                    ))}
                </TableBody>
            </Table>
        </div>
    );
};

export default ExchangeAuditTab;


