import {Button, Text} from "@fluentui/react-components";
import {DismissCircleRegular} from "@fluentui/react-icons";
import {useStepCommunicationRowStyles} from "./StepCommunicationRowStyles.tsx";

interface StepCommunicationRowProps
{
    index: number;
    communicationId: string | undefined;
    selectedCommunicationName: string | null;
    onPick: () => void;
    onClear: () => void;
}

const StepCommunicationRow = ({index, communicationId, selectedCommunicationName, onPick, onClear}: StepCommunicationRowProps) =>
{
    const styles = useStepCommunicationRowStyles();

    return (
        <div id={"step-card-div-12"}
             className={styles.notificationRow}>
            <Button id={`step-card-pick-communication-btn-${index}`}
                    size="small"
                    appearance="secondary"
                    shape="circular"
                    onClick={onPick}>
                {selectedCommunicationName ?? "Select communication..."}
            </Button>
            {communicationId && (
                <Button id={`step-card-clear-communication-btn-${index}`}
                        size="small"
                        appearance="subtle"
                        shape="circular"
                        icon={<DismissCircleRegular/>}
                        onClick={onClear}>
                    Clear
                </Button>
            )}
            {!communicationId && (
                <Text id={"step-card-text-6"}
                      size={200}
                      className={styles.notificationHint}>
                    Leave blank to use the default system notification.
                </Text>
            )}
        </div>
    );
};

export default StepCommunicationRow;
