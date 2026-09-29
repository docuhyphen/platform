import {useState} from "react";
import {Button} from "@fluentui/react-components";
import {InformationRequestClockChangePath} from "../../../../services/informationRequestAdministrationService.ts";
import {InformationRequestClockDto, InformationRequestClockState} from "../../../models/models.tsx";
import {humanizedKey} from "../../submission/submissionLabels.ts";
import ClockChangeDialog from "../clock-change-dialog/ClockChangeDialog.tsx";
import {useClockCommandButtonsStyles} from "./ClockCommandButtonsStyles.tsx";

interface Props
{
    clock: InformationRequestClockDto;
    busy: boolean;
    onChange: (clock: InformationRequestClockDto, path: InformationRequestClockChangePath, reasonCode: string, extensionMinutes?: number) => void;
}

const COMMANDS: {path: InformationRequestClockChangePath; label: string; states: InformationRequestClockState[]}[] = [
    {path: "pauses", label: "Pause", states: [InformationRequestClockState.RUNNING]},
    {path: "resumptions", label: "Resume", states: [InformationRequestClockState.PAUSED]},
    {path: "extensions", label: "Extend", states: [InformationRequestClockState.RUNNING, InformationRequestClockState.PAUSED]},
];

const ClockCommandButtons = ({clock, busy, onChange}: Props) =>
{
    const styles = useClockCommandButtonsStyles();
    const [path, setPath] = useState<InformationRequestClockChangePath | null>(null);
    const available = COMMANDS.filter(command => command.states.includes(clock.state));
    const name = humanizedKey(clock.clockKey);

    if (available.length === 0) return null;

    return (
        <div id={`information-request-clock-${clock.id}-commands`}
             className={styles.buttons}>
            {available.map(command => (
                <Button key={command.path}
                        id={`information-request-clock-${clock.id}-${command.path}`}
                        size={"small"}
                        shape={"circular"}
                        aria-label={`${command.label} ${name} clock`}
                        disabled={busy}
                        onClick={() => setPath(command.path)}>
                    {command.label}
                </Button>
            ))}
            {path && (
                <ClockChangeDialog clock={clock}
                                   path={path}
                                   busy={busy}
                                   onConfirm={(reasonCode, extensionMinutes) =>
                                   {
                                       setPath(null);
                                       onChange(clock, path, reasonCode, extensionMinutes);
                                   }}
                                   onDismiss={() => setPath(null)}/>
            )}
        </div>
    );
};

export default ClockCommandButtons;
