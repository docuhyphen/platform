import {MessageBar, MessageBarBody, Spinner, Text, Title3} from "@fluentui/react-components";
import ClockCommandButtons from "../../clocks/clock-command-buttons/ClockCommandButtons.tsx";
import ClockStartForm from "../../clocks/clock-start-form/ClockStartForm.tsx";
import {useRequestClocks} from "../../clocks/useRequestClocks.ts";
import {clockStateLabels} from "../../operations/operationsLabels.ts";
import {formatInformationRequestTime} from "../../shared/informationRequestFormatting.ts";
import {humanizedKey} from "../../submission/submissionLabels.ts";
import {useRequestClockPanelStyles} from "./RequestClockPanelStyles.tsx";

interface Props
{
    requestId: string;
    editable: boolean;
}

const RequestClockPanel = ({requestId, editable}: Props) =>
{
    const styles = useRequestClockPanelStyles();
    const state = useRequestClocks(requestId);

    return (
        <section id={"information-request-clock-panel"}
                 aria-labelledby={"information-request-clock-panel-title"}
                 className={styles.panel}>
            <Title3 id={"information-request-clock-panel-title"}
                    as={"h2"}>
                Due dates
            </Title3>
            {state.error && (
                <MessageBar id={"information-request-clock-panel-error"}
                            intent={"error"}>
                    <MessageBarBody>{state.error}</MessageBarBody>
                </MessageBar>
            )}
            {!state.loaded && (
                <Spinner id={"information-request-clock-panel-loading"}
                         size={"tiny"}
                         label={"Loading clocks"}/>
            )}
            {state.loaded && state.clocks.length === 0 && (
                <Text id={"information-request-clock-panel-empty"}
                      className={styles.muted}>
                    No clock runs for this request.
                </Text>
            )}
            {state.clocks.length > 0 && (
                <ul id={"information-request-clock-panel-list"}
                    className={styles.list}>
                    {state.clocks.map(clock => (
                        <li key={clock.id}
                            id={`information-request-clock-panel-${clock.id}`}
                            className={styles.clock}>
                            <div id={`information-request-clock-panel-${clock.id}-text`}
                                 className={styles.clockText}>
                                <Text weight={"semibold"}>
                                    {`${humanizedKey(clock.clockKey)}: ${clockStateLabels[clock.state]}`}
                                </Text>
                                <Text size={200}
                                      className={styles.muted}>
                                    {`Due ${formatInformationRequestTime(clock.dueAt)}`}
                                </Text>
                            </div>
                            {editable && (
                                <ClockCommandButtons clock={clock}
                                                     busy={state.busy}
                                                     onChange={(target, path, reason, minutes) => void state.change(target, path, reason, minutes)}/>
                            )}
                        </li>
                    ))}
                </ul>
            )}
            {editable && (
                <ClockStartForm choices={state.choices}
                                busy={state.busy}
                                onStart={(versionId, urgency, key) => void state.start(versionId, urgency, key)}/>
            )}
        </section>
    );
};

export default RequestClockPanel;
