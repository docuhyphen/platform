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
            <div id={"exchange-audit-tab-div-1"}
                 className={styles.spinner}>
                <Spinner id={"exchange-audit-tab-spinner-1"}
                         size="small"
                         label="Loading audit logs..."
                         labelPosition="after"/>
            </div>
        );
    }

    if (error)
    {
        return <Text id={"exchange-audit-tab-text-1"}
                     className={styles.errorText}>Error: {error}</Text>;
    }

    return (
        <div id={"exchange-audit-tab-container"}
             className={styles.container}>
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
