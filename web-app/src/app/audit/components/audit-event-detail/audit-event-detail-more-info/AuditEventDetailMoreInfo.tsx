import {
    Accordion,
    AccordionHeader,
    AccordionItem,
    AccordionPanel,
    Text,
} from "@fluentui/react-components";
import {AuditEventDto} from "../../../../models/models.tsx";
import {formatDateTime} from "../../../../helpers.ts";
import {
    formatAuditEventType,
    formatAuditOutcome,
    formatAuditPayloadKey,
    formatAuditPayloadValue,
    getAuditPayloadEntries,
} from "../../../auditDisplayFormatters.ts";
import AuditEventDetailField from "../audit-event-detail-field/AuditEventDetailField.tsx";
import {useAuditEventDetailStyles} from "../AuditEventDetailStyles.tsx";
import {useAuditEventDetailMoreInfoStyles} from "./AuditEventDetailMoreInfoStyles.tsx";

interface AuditEventDetailMoreInfoProps
{
    event: AuditEventDto;
}

const AuditEventDetailMoreInfo = ({event}: AuditEventDetailMoreInfoProps) =>
{
    const detailStyles = useAuditEventDetailStyles();
    const styles = useAuditEventDetailMoreInfoStyles();
    const payloadEntries = getAuditPayloadEntries(event);

    return (
        <Accordion id={"audit-event-detail-more-info"}
                   collapsible>
            <AccordionItem id={"audit-event-detail-more-info-accordion-item-1"}
                           value={"more-info"}>
                <AccordionHeader id={"audit-event-detail-more-info-accordion-header-1"}>More info</AccordionHeader>
                <AccordionPanel id={"audit-event-detail-more-info-accordion-panel-1"}>
                    <div id={"audit-event-detail-more-info-div-1"}
                         className={styles.fieldGrid}>
                        <AuditEventDetailField label={"Outcome"}
                                               value={formatAuditOutcome(event.outcome)}/>
                        {event.organizationLabel && (
                            <AuditEventDetailField label={"Organization"}
                                                   value={event.organizationLabel}/>
                        )}
                        <AuditEventDetailField label={"Event type"}
                                               value={formatAuditEventType(event.eventTypeKey)}/>
                        <AuditEventDetailField label={"Ledger time"}
                                               value={formatDateTime(event.ledgerTime)}/>
                        <AuditEventDetailField label={"Verification hash"}
                                               value={event.eventHash}/>
                        {event.prevHash && (
                            <AuditEventDetailField label={"Previous verification hash"}
                                                   value={event.prevHash}/>
                        )}
                    </div>

                    {payloadEntries.length > 0 && (
                        <div id={"audit-event-detail-payload"}>
                            <Text id={"audit-event-detail-more-info-text-1"}
                                  size={200}
                                  className={detailStyles.fieldLabel}>Payload</Text>
                            <div id={"audit-event-detail-more-info-div-2"}
                                 className={styles.payloadList}>
                                {payloadEntries.map(([key, value]) => (
                                    <div id={`audit-event-detail-more-info-div-3-${key}`}
                                         key={key}
                                         className={styles.payloadRow}>
                                        <Text id={"audit-event-detail-more-info-text-2"}
                                              weight={"semibold"}>{formatAuditPayloadKey(key)}:</Text>
                                        <Text id={"audit-event-detail-more-info-text-3"}>{formatAuditPayloadValue(key, value, event)}</Text>
                                    </div>
                                ))}
                            </div>
                        </div>
                    )}

                    <Text id={"audit-event-detail-redaction-note"}
                          size={200}
                          className={styles.note}>
                        Some fields may be masked or withheld based on your access.
                    </Text>
                </AccordionPanel>
            </AccordionItem>
        </Accordion>
    );
};

export default AuditEventDetailMoreInfo;
