import {Badge, Button, Text} from "@fluentui/react-components";
import {InformationRequestAuditEventDto} from "../../../models/models.tsx";
import {AuditFilterField} from "../audit-search/useAuditSearch.ts";
import {auditActorText, countedText, formattedTime, shortId} from "../operationsLabels.ts";
import {useAuditEventRowStyles} from "./AuditEventRowStyles.tsx";

interface AuditEventRowProps
{
    event: InformationRequestAuditEventDto;
    showRequest?: boolean;
    onFilter?: (field: AuditFilterField, value: string) => void;
}

const auditPayloadText = (event: InformationRequestAuditEventDto): string =>
{
    const shown = Object.entries(event.payload).map(([key, value]) => `${key}: ${value}`).join(", ");
    if (event.withheldKeyCount === 0) return shown;
    const withheld = countedText(event.withheldKeyCount, "value withheld", "values withheld");
    return shown ? `${shown}, ${withheld}` : withheld;
};

const whenText = (event: InformationRequestAuditEventDto, showRequest: boolean): string =>
{
    const when = `${formattedTime(event.occurredAt)}, ${event.outcome.toLowerCase()}, by ${auditActorText(event.actorKind)}`;
    return showRequest && event.requestId ? `${when}, request ${shortId(event.requestId)}` : when;
};

const AuditEventRow = ({event, showRequest = false, onFilter}: AuditEventRowProps) =>
{
    const styles = useAuditEventRowStyles();
    const id = `information-request-audit-event-${event.eventId}`;
    const payload = auditPayloadText(event);
    const {actorId, requestId} = event;

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
            {onFilter && (actorId || requestId) && (
                <div id={`${id}-filters`}
                     className={styles.filters}>
                    {actorId && (
                        <Button id={`${id}-filter-actor`}
                                appearance={"secondary"}
                                shape={"circular"}
                                size={"small"}
                                aria-label={`Search the events of actor ${actorId}`}
                                onClick={() => onFilter("actorId", actorId)}>
                            This actor
                        </Button>
                    )}
                    {requestId && (
                        <Button id={`${id}-filter-request`}
                                appearance={"secondary"}
                                shape={"circular"}
                                size={"small"}
                                aria-label={`Search the events of request ${requestId}`}
                                onClick={() => onFilter("requestId", requestId)}>
                            This request
                        </Button>
                    )}
                </div>
            )}
        </li>
    );
};

export default AuditEventRow;
