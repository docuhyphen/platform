import {useCallback} from "react";
import {Badge, Text} from "@fluentui/react-components";
import {useLoadedValue} from "../../../../hooks/useLoadedValue.ts";
import {
    getInformationRequestAuditEvents,
    getInformationRequestAuditReconciliation,
} from "../../../../services/informationRequestOperationsService.ts";
import {InformationRequestAuditEventDto} from "../../../models/models.tsx";
import AuditReconciliationSummary from "../audit-reconciliation-summary/AuditReconciliationSummary.tsx";
import LoadedPanel from "../loaded-panel/LoadedPanel.tsx";
import {formattedTime} from "../operationsLabels.ts";
import {useAuditHistoryPanelStyles} from "./AuditHistoryPanelStyles.tsx";

interface AuditHistoryPanelProps
{
    requestId: string;
}

const payloadOf = (event: InformationRequestAuditEventDto): string =>
{
    const shown = Object.entries(event.payload).map(([key, value]) => `${key}: ${value}`).join(", ");
    if (event.withheldKeyCount === 0) return shown;
    const withheld = `${event.withheldKeyCount} values withheld`;
    return shown ? `${shown}, ${withheld}` : withheld;
};

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
                        {value.items.map(event =>
                        {
                            const id = `information-request-audit-event-${event.eventId}`;
                            const payload = payloadOf(event);
                            return (
                                <li id={id}
                                    key={event.eventId}
                                    className={styles.event}>
                                    <div id={`${id}-heading`}
                                         className={styles.heading}>
                                        <Text id={`${id}-type`}
                                              weight={"semibold"}>
                                            {event.eventTypeKey}
                                        </Text>
                                        <Badge id={`${id}-sealed`}
                                               appearance={"outline"}
                                               color={event.sealed ? "success" : "warning"}>
                                            {event.sealed ? "Sealed" : "Not sealed"}
                                        </Badge>
                                    </div>
                                    <Text id={`${id}-when`}
                                          className={styles.detail}>
                                        {`${formattedTime(event.occurredAt)}, ${event.outcome.toLowerCase()}, by ${event.actorKind?.toLowerCase() ?? "the platform"}`}
                                    </Text>
                                    {payload && (
                                        <Text id={`${id}-payload`}
                                              className={styles.detail}>
                                            {payload}
                                        </Text>
                                    )}
                                </li>
                            );
                        })}
                    </ul>
                )}
            </LoadedPanel>
        </div>
    );
};

export default AuditHistoryPanel;
