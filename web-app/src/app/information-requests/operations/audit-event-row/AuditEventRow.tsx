import {Badge, Text} from "@fluentui/react-components";
import {InformationRequestAuditEventDto} from "../../../models/models.tsx";
import {formattedTime, shortId} from "../operationsLabels.ts";
import {useAuditEventRowStyles} from "./AuditEventRowStyles.tsx";

interface AuditEventRowProps
{
    event: InformationRequestAuditEventDto;
    showRequest?: boolean;
}

const auditPayloadText = (event: InformationRequestAuditEventDto): string =>
{
    const shown = Object.entries(event.payload).map(([key, value]) => `${key}: ${value}`).join(", ");
    if (event.withheldKeyCount === 0) return shown;
    const withheld = `${event.withheldKeyCount} values withheld`;
    return shown ? `${shown}, ${withheld}` : withheld;
};

const whenText = (event: InformationRequestAuditEventDto, showRequest: boolean): string =>
{
    const when = `${formattedTime(event.occurredAt)}, ${event.outcome.toLowerCase()}, by ${event.actorKind?.toLowerCase() ?? "the platform"}`;
    return showRequest && event.requestId ? `${when}, request ${shortId(event.requestId)}` : when;
};

const AuditEventRow = ({event, showRequest = false}: AuditEventRowProps) =>
{
    const styles = useAuditEventRowStyles();
    const id = `information-request-audit-event-${event.eventId}`;
    const payload = auditPayloadText(event);

    return (
        <li id={id}
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
                {whenText(event, showRequest)}
            </Text>
            {payload && (
                <Text id={`${id}-payload`}
                      className={styles.detail}>
                    {payload}
                </Text>
            )}
        </li>
    );
};

export default AuditEventRow;
