import {
    Accordion,
    AccordionHeader,
    AccordionItem,
    AccordionPanel,
    Text,
} from "@fluentui/react-components";
import {AuditEventDto} from "../../../models/models.tsx";
import {formatDateTime} from "../../../helpers.ts";
import {
    formatAuditEventType,
    formatAuditOutcome,
    formatAuditPayloadKey,
    formatAuditPayloadValue,
    getAuditPayloadEntries,
} from "../../auditDisplayFormatters.ts";
import AuditEventDetailField from "./AuditEventDetailField.tsx";
import {useAuditEventDetailStyles} from "./AuditEventDetailStyles.tsx";

interface AuditEventDetailMoreInfoProps
{
    event: AuditEventDto;
}

const AuditEventDetailMoreInfo = ({event}: AuditEventDetailMoreInfoProps) =>
{
    const styles = useAuditEventDetailStyles();
    const payloadEntries = getAuditPayloadEntries(event);

    return (
        <Accordion id={"audit-event-detail-more-info"} collapsible>
            <AccordionItem value={"more-info"}>
                <AccordionHeader>More info</AccordionHeader>
                <AccordionPanel>
                    <div className={styles.fieldGrid}>
                        <AuditEventDetailField label={"Outcome"} value={formatAuditOutcome(event.outcome)}/>
                        {event.organizationLabel && (
                            <AuditEventDetailField label={"Organization"} value={event.organizationLabel}/>
                        )}
                        <AuditEventDetailField label={"Event type"} value={formatAuditEventType(event.eventTypeKey)}/>
                        <AuditEventDetailField label={"Ledger time"} value={formatDateTime(event.ledgerTime)}/>
                        <AuditEventDetailField label={"Verification hash"} value={event.eventHash}/>
                        {event.prevHash && (
                            <AuditEventDetailField label={"Previous verification hash"} value={event.prevHash}/>
                        )}
                    </div>

                    {payloadEntries.length > 0 && (
                        <div id={"audit-event-detail-payload"}>
                            <Text size={200} className={styles.fieldLabel}>Payload</Text>
                            <div className={styles.payloadList}>
                                {payloadEntries.map(([key, value]) => (
                                    <div key={key} className={styles.payloadRow}>
                                        <Text weight={"semibold"}>{formatAuditPayloadKey(key)}:</Text>
                                        <Text>{formatAuditPayloadValue(key, value, event)}</Text>
                                    </div>
                                ))}
                            </div>
                        </div>
                    )}

                    <Text id={"audit-event-detail-redaction-note"} size={200} className={styles.note}>
                        Some fields may be masked or withheld based on your access.
                    </Text>
                </AccordionPanel>
            </AccordionItem>
        </Accordion>
    );
};

export default AuditEventDetailMoreInfo;
