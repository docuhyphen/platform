import {useState} from "react";
import {Spinner, Text} from "@fluentui/react-components";
import {AuditEventDto, ExchangeDetailedDto} from "../../../models/models.tsx";
import {fetchExchangeLedgerEvents} from "../../../../services/auditService.ts";
import {useAuditEventPage} from "../../../audit/components/use-audit-event-page/useAuditEventPage.ts";
import AuditEventTable from "../../../audit/components/audit-event-table/AuditEventTable.tsx";
import AuditEventDetail from "../../../audit/components/audit-event-detail/AuditEventDetail.tsx";
import {useExchangeAuditTabStyles} from "./ExchangeAuditTabStyles.tsx";

interface ExchangeAuditTabProps
{
    exchange: ExchangeDetailedDto;
}

/**
 * Ledger-backed Exchange "Audit" tab (Phase 7): every document event plus Exchange-level events,
 * paginated over `GET /exchanges/{exchangeId}/audit-events`, using the shared audit table/detail
 * components instead of a bespoke table.
 */
const ExchangeAuditTab = ({exchange}: ExchangeAuditTabProps) =>
{
    const styles = useExchangeAuditTabStyles();
    const [selectedEvent, setSelectedEvent] = useState<AuditEventDto | null>(null);
    const {items, loading, error, cursor, loadMore} = useAuditEventPage((params) =>
        fetchExchangeLedgerEvents(exchange.id, params)
    );

    if (loading && items.length === 0)
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

    return (
        <div id={"exchange-audit-tab-container"} className={styles.container}>
            <AuditEventTable
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

export default ExchangeAuditTab;
