import {useCallback} from "react";
import {Badge, Text} from "@fluentui/react-components";
import {useLoadedValue} from "../../../../hooks/useLoadedValue.ts";
import {getInformationRequestClocks} from "../../../../services/informationRequestOperationsService.ts";
import {InformationRequestClockState} from "../../../models/models.tsx";
import {humanizedKey} from "../../submission/submissionLabels.ts";
import LoadedPanel from "../loaded-panel/LoadedPanel.tsx";
import {clockEventLabels, clockStateLabels, formattedDuration, formattedTime} from "../operationsLabels.ts";
import {useClockHistoryPanelStyles} from "./ClockHistoryPanelStyles.tsx";

interface ClockHistoryPanelProps
{
    requestId: string;
}

const ClockHistoryPanel = ({requestId}: ClockHistoryPanelProps) =>
{
    const styles = useClockHistoryPanelStyles();
    const load = useCallback(() => getInformationRequestClocks(requestId), [requestId]);
    const clocks = useLoadedValue(load, "The clocks could not be loaded.");

    return (
        <LoadedPanel idPrefix={"information-request-clocks"}
                     loaded={clocks}
                     loadingLabel={"Loading clocks"}
                     emptyText={"No clocks run for this request."}
                     isEmpty={value => value.length === 0}>
            {value => (
                <ul id={"information-request-clocks"}
                    className={styles.list}>
                    {value.map(clock =>
                    {
                        const id = `information-request-clock-${clock.id}`;
                        return (
                            <li id={id}
                                key={clock.id}
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
                                    {`Due ${formattedTime(clock.dueAt)}, cycle ${clock.dueCycle}, policy version ${clock.policyVersionNumber}`}
                                </Text>
                                {clock.remainingSeconds !== undefined && (
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
                                <ol id={`${id}-events`}
                                    className={styles.events}>
                                    {clock.events.map(event => (
                                        <li id={`${id}-event-${event.eventNumber}`}
                                            key={event.eventNumber}
                                            className={styles.detail}>
                                            {event.reasonCode
                                                ? `${clockEventLabels[event.eventKind]} ${formattedTime(event.occurredAt)}, ${humanizedKey(event.reasonCode)}`
                                                : `${clockEventLabels[event.eventKind]} ${formattedTime(event.occurredAt)}`}
                                        </li>
                                    ))}
                                </ol>
                            </li>
                        );
                    })}
                </ul>
            )}
        </LoadedPanel>
    );
};

export default ClockHistoryPanel;
