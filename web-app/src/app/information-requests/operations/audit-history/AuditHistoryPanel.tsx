import {useCallback} from "react";
import {useLoadedValue} from "../../../../hooks/useLoadedValue.ts";
import {
    getInformationRequestAuditEvents,
    getInformationRequestAuditReconciliation,
} from "../../../../services/informationRequestOperationsService.ts";
import AuditEventRow from "../audit-event-row/AuditEventRow.tsx";
import AuditReconciliationSummary from "../audit-reconciliation-summary/AuditReconciliationSummary.tsx";
import LoadedPanel from "../loaded-panel/LoadedPanel.tsx";
import {useAuditHistoryPanelStyles} from "./AuditHistoryPanelStyles.tsx";

interface AuditHistoryPanelProps
{
    requestId: string;
}

const AuditHistoryPanel = ({requestId}: AuditHistoryPanelProps) =>
{
    const styles = useAuditHistoryPanelStyles();
    const loadReconciliation = useCallback(() => getInformationRequestAuditReconciliation(requestId), [requestId]);
    const loadEvents = useCallback(() => getInformationRequestAuditEvents(requestId), [requestId]);
    const reconciliation = useLoadedValue(loadReconciliation, "The audit reconciliation could not be loaded.");
    const events = useLoadedValue(loadEvents, "The audit history could not be loaded.");

    return (
        <div id={"information-request-audit-history"}
             className={styles.panel}>
            <LoadedPanel idPrefix={"information-request-audit-reconciliation"}
                         loaded={reconciliation}
                         loadingLabel={"Reconciling the audit history"}>
                {value => <AuditReconciliationSummary reconciliation={value}/>}
            </LoadedPanel>
            <LoadedPanel idPrefix={"information-request-audit-events"}
                         loaded={events}
                         loadingLabel={"Loading the audit history"}
                         emptyText={"No audit records exist for this request yet."}
                         isEmpty={value => value.items.length === 0}>
                {value => (
                    <ul id={"information-request-audit-events"}
                        className={styles.list}>
                        {value.items.map(event => (
                            <AuditEventRow key={event.eventId}
                                           event={event}/>
                        ))}
                    </ul>
                )}
            </LoadedPanel>
        </div>
    );
};

export default AuditHistoryPanel;
