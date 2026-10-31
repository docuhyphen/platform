import {useCallback, useState} from "react";
import {useLoadedValue} from "../../../../hooks/useLoadedValue.ts";
import {
    getInformationRequestAuditEvents,
    getInformationRequestAuditReconciliation,
} from "../../../../services/informationRequestOperationsService.ts";
import AuditEventRow from "../audit-event-row/AuditEventRow.tsx";
import AuditPager from "../audit-pager/AuditPager.tsx";
import AuditReconciliationSummary from "../audit-reconciliation-summary/AuditReconciliationSummary.tsx";
import LoadedPanel from "../loaded-panel/LoadedPanel.tsx";
import {reportingForbidden} from "../operations-detail-content/reportingForbidden.ts";
import {useAuditHistoryPanelStyles} from "./AuditHistoryPanelStyles.tsx";

interface AuditHistoryPanelProps
{
    requestId: string;
    onForbidden?: () => void;
}

const AUDIT_HISTORY_PAGE_SIZE = 50;

const AuditHistoryPanel = ({requestId, onForbidden}: AuditHistoryPanelProps) =>
{
    const styles = useAuditHistoryPanelStyles();
    const [offset, setOffset] = useState(0);
    const loadReconciliation = useCallback(
        () => reportingForbidden(getInformationRequestAuditReconciliation(requestId), onForbidden),
        [requestId, onForbidden],
    );
    const loadEvents = useCallback(
        () => reportingForbidden(getInformationRequestAuditEvents(requestId, {
            limit: AUDIT_HISTORY_PAGE_SIZE,
            offset
        }), onForbidden),
        [requestId, offset, onForbidden],
    );
    const reconciliation = useLoadedValue(loadReconciliation, "The audit reconciliation could not be loaded.");
    const events = useLoadedValue(loadEvents, "The audit history could not be loaded.", requestId);

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
                         isEmpty={value => value.total === 0}>
                {value => (
                    <>
                        <ul id={"information-request-audit-events"}
                            aria-label={"Audit records, newest first"}
                            className={styles.list}>
                            {value.items.map(event => (
                                <AuditEventRow key={event.eventId}
                                               event={event}/>
                            ))}
                        </ul>
                        <AuditPager idPrefix={"information-request-audit-events"}
                                    offset={offset}
                                    pageSize={AUDIT_HISTORY_PAGE_SIZE}
                                    total={value.total}
                                    onOffsetChange={setOffset}/>
                    </>
                )}
            </LoadedPanel>
        </div>
    );
};

export default AuditHistoryPanel;
