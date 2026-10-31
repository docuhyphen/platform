import {Badge, Text} from "@fluentui/react-components";
import {InformationRequestClockDto, InformationRequestClockState} from "../../../models/models.tsx";
import {humanizedKey} from "../../submission/submissionLabels.ts";
import {clockEventSentence, clockInputsText} from "../clock-history/clockHistoryText.ts";
import {clockStateLabels, formattedDuration, formattedTime} from "../operationsLabels.ts";
import {useClockCardStyles} from "./ClockCardStyles.tsx";

interface ClockCardProps {
    clock: InformationRequestClockDto;
    policy: string;
}

const ClockCard = ({clock, policy}: ClockCardProps) => {
    const styles = useClockCardStyles();
    const id = `information-request-clock-${clock.id}`;

    return (
        <li id={id}
            className={styles.clock}>
            <div id={`${id}-heading`}
                 className={styles.heading}>
                <Text id={`${id}-key`}
                      weight={"semibold"}>
                    {humanizedKey(clock.clockKey)}
                </Text>
                <Badge id={`${id}-state`}
                       appearance={"outline"}
                       color={clock.state === InformationRequestClockState.RUNNING ? "brand" : "informative"}>
                    {clockStateLabels[clock.state]}
                </Badge>
            </div>
            <Text id={`${id}-due`}
                  className={styles.detail}>
                {`Due ${formattedTime(clock.dueAt)}, cycle ${clock.dueCycle}`}
            </Text>
            <Text id={`${id}-inputs`}
                  className={styles.detail}>
                {clockInputsText(clock)}
            </Text>
            <Text id={`${id}-policy`}
                  className={styles.detail}>
                {policy}
            </Text>
            {clock.remainingSeconds !== undefined && clock.remainingSeconds !== null && (
                <Text id={`${id}-remaining`}
                      className={styles.detail}>
                    {`Time left when paused: ${formattedDuration(clock.remainingSeconds)}`}
                </Text>
            )}
            {clock.overdueAt && (
                <Text id={`${id}-overdue`}
                      className={styles.overdue}>
                    {`Overdue since ${formattedTime(clock.overdueAt)}`}
                </Text>
            )}
            {clock.stoppedAt && (
                <Text id={`${id}-stopped`}
                      className={styles.detail}>
                    {`Stopped ${formattedTime(clock.stoppedAt)}`}
                </Text>
            )}
            <ol id={`${id}-events`}
                aria-label={`${humanizedKey(clock.clockKey)} history`}
                className={styles.events}>
                {clock.events.map(event => (
                    <li id={`${id}-event-${event.eventNumber}`}
                        key={event.eventNumber}
                        className={styles.detail}>
                        {clockEventSentence(event)}
                    </li>
                ))}
            </ol>
        </li>
    );
};

export default ClockCard;
