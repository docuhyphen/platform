import {Button, Text} from "@fluentui/react-components";
import {useOperationsQueueToolbarStyles} from "./OperationsQueueToolbarStyles.tsx";

interface OperationsQueueToolbarProps
{
    selectedCount: number;
    canSendReminders: boolean;
    busy: boolean;
    onSendReminders: () => void;
    onExport: () => void;
}

const OperationsQueueToolbar = ({selectedCount, canSendReminders, busy, onSendReminders, onExport}: OperationsQueueToolbarProps) =>
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
                    {selectedCount > 0 ? `${selectedCount} selected` : "Select requests to remind their responding parties."}
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
