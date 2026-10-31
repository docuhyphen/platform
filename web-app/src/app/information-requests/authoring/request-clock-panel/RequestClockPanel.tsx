import {MessageBar, MessageBarBody, Spinner, Text, Title3} from "@fluentui/react-components";
import {InformationRequestClockChangePath} from "../../../../services/informationRequestAdministrationService.ts";
import {InformationRequestClockDto, InformationRequestClockState} from "../../../models/models.tsx";
import ClockCommandButtons from "../../clocks/clock-command-buttons/ClockCommandButtons.tsx";
import ClockStartForm from "../../clocks/clock-start-form/ClockStartForm.tsx";
import {useRequestClocks} from "../../clocks/useRequestClocks.ts";
import {clockStateLabels, formattedDuration} from "../../operations/operationsLabels.ts";
import {formatInformationRequestTime} from "../../shared/informationRequestFormatting.ts";
import InformationRequestLoadError from "../../shared/load-error/InformationRequestLoadError.tsx";
import {InformationRequestOwnerFields} from "../../shared/ownerWorkspace.ts";
import {humanizedKey} from "../../submission/submissionLabels.ts";
import {ClockActionPermissions} from "../author-workspace-content/authorActionPermissions.ts";
import {useRequestClockPanelStyles} from "./RequestClockPanelStyles.tsx";

interface Props
{
    requestId: string;
    owner: InformationRequestOwnerFields;
    permissions: ClockActionPermissions;
}

const dueText = (clock: InformationRequestClockDto): string =>
    clock.state === InformationRequestClockState.STOPPED ? "Stopped; no active deadline"
        : clock.state === InformationRequestClockState.PAUSED && clock.remainingSeconds !== undefined && clock.remainingSeconds !== null
            ? `Time left when paused: ${formattedDuration(clock.remainingSeconds)}`
            : `Due ${formatInformationRequestTime(clock.dueAt)}`;

const permittedChanges = (permissions: ClockActionPermissions): InformationRequestClockChangePath[] => [
    ...(permissions.pauseOrExtend ? ["pauses", "extensions"] as const : []),
    ...(permissions.startOrResume ? ["resumptions"] as const : []),
];

const RequestClockPanel = ({requestId, owner, permissions}: Props) =>
{
    const styles = useRequestClockPanelStyles();
    const state = useRequestClocks(requestId, owner);
    const clocks = state.clocks;
    const changes = permittedChanges(permissions);

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
                            intent={"error"}
                            role={"alert"}>
                    <MessageBarBody id={"information-request-clock-panel-error-body"}>{state.error}</MessageBarBody>
                </MessageBar>
            )}
            {state.loadError && (
                <InformationRequestLoadError id={"information-request-clock-panel-load-error"}
                                             message={state.loadError}
                                             onRetry={() => void state.reload()}/>
            )}
            {clocks === null && !state.loadError && (
                <Spinner id={"information-request-clock-panel-loading"}
                         size={"tiny"}
                         label={"Loading clocks"}/>
            )}
            {clocks !== null && clocks.length === 0 && (
                <Text id={"information-request-clock-panel-empty"}
                      className={styles.muted}>
                    No clock runs for this request.
                </Text>
            )}
            {clocks !== null && clocks.length > 0 && (
                <ul id={"information-request-clock-panel-list"}
                    className={styles.list}>
                    {clocks.map(clock => (
                        <li key={clock.id}
                            id={`information-request-clock-panel-${clock.id}`}
                            className={styles.clock}>
                            <div id={`information-request-clock-panel-${clock.id}-text`}
                                 className={styles.clockText}>
                                <Text id={`information-request-clock-panel-${clock.id}-state`}
                                      weight={"semibold"}>
                                    {`${humanizedKey(clock.clockKey)}: ${clockStateLabels[clock.state]}`}
                                </Text>
                                <Text id={`information-request-clock-panel-${clock.id}-due`}
                                      size={200}
                                      className={styles.muted}>
                                    {dueText(clock)}
                                </Text>
                            </div>
                            {changes.length > 0 && (
                                <ClockCommandButtons clock={clock}
                                                     permitted={changes}
                                                     busy={state.busy}
                                                     onChange={(target, path, reason, minutes) => void state.change(target, path, reason, minutes)}/>
                            )}
                        </li>
                    ))}
                </ul>
            )}
            {permissions.startOrResume && state.policiesInOtherWorkspace && (
                <Text id={"information-request-clock-panel-other-workspace"}
                      className={styles.muted}>
                    Due date policies belong to the workspace that owns this request. Switch to it to start a clock.
                </Text>
            )}
            {permissions.startOrResume && clocks !== null && state.choices !== null && (
                <ClockStartForm choices={state.choices}
                                busy={state.busy}
                                onStart={(versionId, urgency, key) => void state.start(versionId, urgency, key)}/>
            )}
        </section>
    );
};

export default RequestClockPanel;
