import {Badge, Button, Text} from "@fluentui/react-components";
import {useNavigate} from "react-router-dom";
import {
    InformationRequestNoticeDeliveryState,
    InformationRequestOperationsException,
    InformationRequestOperationsRowDto,
} from "../../../models/models.tsx";
import {
    exceptionLabels,
    formattedDuration,
    formattedTime,
    noticeCountLabels,
    requestStateLabels,
    shortId,
    slaStatusPresentation,
} from "../operationsLabels.ts";
import {useOperationsQueueRowStyles} from "./OperationsQueueRowStyles.tsx";

interface OperationsQueueRowProps
{
    row: InformationRequestOperationsRowDto;
}

const OperationsQueueRow = ({row}: OperationsQueueRowProps) =>
{
    const styles = useOperationsQueueRowStyles();
    const navigate = useNavigate();
    const id = `information-request-operations-row-${row.requestId}`;
    const sla = slaStatusPresentation[row.slaStatus];
    const notices = Object.values(InformationRequestNoticeDeliveryState)
        .filter(state => (row.noticeCounts[state] ?? 0) > 0)
        .map(state => `${row.noticeCounts[state]} ${noticeCountLabels[state]}`);
    const exceptions = Object.values(InformationRequestOperationsException)
        .filter(exception => (row.exceptionCounts[exception] ?? 0) > 0)
        .map(exception => `${row.exceptionCounts[exception]} ${exceptionLabels[exception]}`);

    return (
        <li id={id}
            className={styles.row}>
            <div id={`${id}-summary`}
                 className={styles.summary}>
                <Text id={`${id}-title`}
                      weight={"semibold"}>
                    {`Request ${shortId(row.requestId)}, ${requestStateLabels[row.state]}`}
                </Text>
                <Text id={`${id}-timing`}
                      className={styles.detail}>
                    {row.nearestDueAt
                        ? `Open ${formattedDuration(row.ageSeconds)}, due ${formattedTime(row.nearestDueAt)}, ${row.reminderCount} reminders`
                        : `Open ${formattedDuration(row.ageSeconds)}, ${row.reminderCount} reminders`}
                </Text>
                {notices.length > 0 && (
                    <Text id={`${id}-notices`}
                          className={styles.detail}>
                        {`Notices: ${notices.join(", ")}`}
                    </Text>
                )}
                {exceptions.length > 0 && (
                    <Text id={`${id}-exceptions`}
                          className={styles.exception}>
                        {`Needs attention: ${exceptions.join(", ")}`}
                    </Text>
                )}
            </div>
            <Badge id={`${id}-sla`}
                   appearance={"outline"}
                   color={sla.color}>
                {sla.label}
            </Badge>
            <Button id={`${id}-open`}
                    appearance={"primary"}
                    shape={"circular"}
                    onClick={() => navigate(`/information-request-operations/${row.requestId}`)}>
                Open
            </Button>
        </li>
    );
};

export default OperationsQueueRow;
