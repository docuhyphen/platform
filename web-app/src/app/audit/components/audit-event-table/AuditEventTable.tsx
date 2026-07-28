import {useEffect, useMemo, useState} from "react";
import {
    Table,
    TableBody,
    TableCell,
    TableRow,
    Text,
} from "@fluentui/react-components";
import {AuditEventCursorDto, AuditEventDto} from "../../../models/models.tsx";
import {formatDateTime} from "../../../helpers.ts";
import {
    formatAuditActor,
    formatAuditEntityLabel,
    formatAuditEventType,
    formatAuditOutcome,
} from "../../auditDisplayFormatters.ts";
import AuditCategoryBadge from "../audit-category-badge/AuditCategoryBadge.tsx";
import {useAuditEventTableStyles} from "./AuditEventTableStyles.tsx";
import AuditEventTableHeader from "./AuditEventTableHeader.tsx";
import AuditEventTableFooter from "./AuditEventTableFooter.tsx";

const AUDIT_EVENTS_PAGE_SIZE = 25;

interface AuditEventTableProps
{
    items: AuditEventDto[];
    nextCursor: AuditEventCursorDto | null;
    onLoadMore: () => void;
    onEventClick: (event: AuditEventDto) => void;
    loading: boolean;
}

/** Shared, paginated table of ledger-backed audit events used across every audit surface. */
const AuditEventTable = (
    {
        items,
        nextCursor,
        onLoadMore,
        onEventClick,
        loading,
    }: AuditEventTableProps
) =>
{
    const styles = useAuditEventTableStyles();
    const [currentPage, setCurrentPage] = useState(0);
    const [pendingPage, setPendingPage] = useState<number | null>(null);
    const loadedPages = Math.max(1, Math.ceil(items.length / AUDIT_EVENTS_PAGE_SIZE));
    const canLoadNextPage = nextCursor !== null;
    const firstItem = items.length === 0 ? 0 : currentPage * AUDIT_EVENTS_PAGE_SIZE + 1;
    const lastItem = Math.min((currentPage + 1) * AUDIT_EVENTS_PAGE_SIZE, items.length);
    const visibleItems = useMemo(() =>
    {
        const start = currentPage * AUDIT_EVENTS_PAGE_SIZE;
        return items.slice(start, start + AUDIT_EVENTS_PAGE_SIZE);
    }, [currentPage, items]);

    useEffect(() =>
    {
        if (pendingPage !== null)
        {
            if (pendingPage <= loadedPages - 1)
            {
                setCurrentPage(pendingPage);
                setPendingPage(null);
            }
            return;
        }

        if (currentPage > loadedPages - 1)
        {
            setCurrentPage(loadedPages - 1);
        }
    }, [currentPage, loadedPages, pendingPage]);

    const handleNextPage = () =>
    {
        if (currentPage < loadedPages - 1)
        {
            setCurrentPage(currentPage + 1);
            return;
        }

        if (canLoadNextPage && !loading)
        {
            setPendingPage(currentPage + 1);
            onLoadMore();
        }
    };

    if (!loading && items.length === 0)
    {
        return (
            <Text id={"audit-event-table-empty"} className={styles.emptyText}>
                No audit events found.
            </Text>
        );
    }

    return (
        <div id={"audit-event-table-container"} className={styles.container}>
            <div
                id={"audit-event-table-scrollable-content"}
                className={styles.tableScroller}>
                <Table id={"audit-event-table"} className={styles.table}>
                    <AuditEventTableHeader className={styles.tableHeader}/>
                    <TableBody id={"audit-event-table-body"}>
                        {visibleItems.map((event) => (
                            <TableRow
                                id={`audit-event-row-${event.eventId}`}
                                key={event.eventId}
                                className={styles.row}
                                onClick={() => onEventClick(event)}
                            >
                                <TableCell>{formatDateTime(event.occurredAt)}</TableCell>
                                <TableCell>
                                    <AuditCategoryBadge category={event.category}/>
                                </TableCell>
                                <TableCell>{formatAuditEventType(event.eventTypeKey)}</TableCell>
                                <TableCell>{formatAuditOutcome(event.outcome)}</TableCell>
                                <TableCell>{formatAuditActor(event.actorKind, event.actorLabel)}</TableCell>
                                <TableCell>{formatAuditEntityLabel(event.targetLabel, event.targetType)}</TableCell>
                            </TableRow>
                        ))}
                    </TableBody>
                </Table>
            </div>

            <AuditEventTableFooter
                loading={loading}
                currentPage={currentPage}
                loadedPages={loadedPages}
                firstItem={firstItem}
                lastItem={lastItem}
                loadedItems={items.length}
                hasMoreItems={canLoadNextPage}
                footerClassName={styles.footer}
                controlsClassName={styles.paginationControls}
                pageClassName={styles.paginationPageIndicator}
                onFirstPage={() => setCurrentPage(0)}
                onPreviousPage={() => setCurrentPage(currentPage - 1)}
                onNextPage={handleNextPage}
                onLastLoadedPage={() => setCurrentPage(loadedPages - 1)}/>
        </div>
    );
};

export default AuditEventTable;
