import {useState} from "react";
import {Spinner, Text} from "@fluentui/react-components";
import {useExchangeDocumentAuditStyles} from "./ExchangeDocumentAuditStyles";
import {AuditEventDto, DocumentDetailedDto} from "../../../../models/models";
import {fetchExchangeDocumentLedgerEvents} from "../../../../../services/auditService.ts";
import {useAuditEventPage} from "../../../../audit/components/use-audit-event-page/useAuditEventPage.ts";
import AuditEventCardList from "../../../../audit/components/audit-event-card-list/AuditEventCardList.tsx";
import AuditEventDetail from "../../../../audit/components/audit-event-detail/AuditEventDetail.tsx";

interface ExchangeDocumentAuditProps
{
    exchangeId: string;
    exchangeDocument: DocumentDetailedDto;
}

/**
 * Ledger-backed document audit view, paginated over
 * `GET /exchanges/{exchangeId}/documents/{documentId}/audit-events` via the shared audit
 * card-list/detail components instead of the legacy single-page audit log list.
 */
const ExchangeDocumentAudit = (
    {
        exchangeId,
        exchangeDocument,
    }: ExchangeDocumentAuditProps
) =>
{
    const styles = useExchangeDocumentAuditStyles();
    const [selectedEvent, setSelectedEvent] = useState<AuditEventDto | null>(null);
    const {items, loading, error, cursor, loadMore} = useAuditEventPage((params) =>
        fetchExchangeDocumentLedgerEvents(exchangeId, exchangeDocument.id, params)
    );

    if (loading && items.length === 0)
    {
        return <Spinner size={"small"}/>;
    }

    if (error)
    {
        return <Text className={styles.error}>Error: {error}</Text>;
    }

    return (
        <div id={"exchange-document-audit-container"} className={styles.auditContainer}>
            <AuditEventCardList
                items={items}
                nextCursor={cursor}
                onLoadMore={loadMore}
                onEventClick={setSelectedEvent}
                loading={loading}
            />

            <AuditEventDetail
                event={selectedEvent}
                open={selectedEvent !== null}
                onDismiss={() => setSelectedEvent(null)}
            />
        </div>
    );
};

export default ExchangeDocumentAudit;
