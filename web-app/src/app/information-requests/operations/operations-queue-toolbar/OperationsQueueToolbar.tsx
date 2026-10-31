import {Button, Text} from "@fluentui/react-components";
import {formatInformationRequestCount} from "../../shared/informationRequestFormatting.ts";
import {useOperationsQueueToolbarStyles} from "./OperationsQueueToolbarStyles.tsx";

interface OperationsQueueToolbarProps
{
    selectedCount: number;
    selectionFull: boolean;
    canSendReminders: boolean;
    busy: boolean;
    onSendReminders: () => void;
    onExport: () => void;
}

const selectionText = (selectedCount: number, selectionFull: boolean): string => {
    if (selectedCount === 0) return "Select issued or in progress requests to remind their responding parties.";
    const selected = `${formatInformationRequestCount(selectedCount)} selected`;
    return selectionFull ? `${selected}, the most that can be reminded at once.` : selected;
};

const OperationsQueueToolbar = ({
                                    selectedCount,
                                    selectionFull,
                                    canSendReminders,
                                    busy,
                                    onSendReminders,
                                    onExport,
                                }: OperationsQueueToolbarProps) =>
{
    const styles = useOperationsQueueToolbarStyles();

    return (
        <div id={"information-request-operations-toolbar"}
             role={"toolbar"}
             aria-label={"Queue actions"}
             className={styles.toolbar}>
            {canSendReminders && (
                <Text id={"information-request-operations-selection"}
                      className={styles.selection}>
                    {selectionText(selectedCount, selectionFull)}
                </Text>
            )}
            {canSendReminders && (
                <Button id={"information-request-operations-remind-btn"}
                        appearance={"primary"}
                        shape={"circular"}
                        disabled={busy || selectedCount === 0}
                        onClick={onSendReminders}>
                    Send reminders
                </Button>
            )}
            <Button id={"information-request-operations-export-btn"}
                    appearance={"secondary"}
                    shape={"circular"}
                    disabled={busy}
                    onClick={onExport}>
                Export CSV
            </Button>
        </div>
    );
};

export default OperationsQueueToolbar;
