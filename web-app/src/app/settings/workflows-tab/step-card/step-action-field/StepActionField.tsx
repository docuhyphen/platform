import {Select, Text} from "@fluentui/react-components";
import {WorkflowStepSpecDraft} from "../../../../models/models.tsx";
import {formatTriggerName} from "../../workflowUtils.ts";
import {BUILT_IN_ACTION_KEYS} from "../stepCardOptions.ts";
import StepCommunicationRow from "../step-communication-row/StepCommunicationRow.tsx";
import {useStepActionFieldStyles} from "./StepActionFieldStyles.tsx";

interface StepActionFieldProps
{
    index: number;
    step: WorkflowStepSpecDraft;
    onPatch: (patch: Partial<WorkflowStepSpecDraft>) => void;
    selectedCommunicationName: string | null;
    onPickCommunication: () => void;
    onClearCommunication: () => void;
}

const StepActionField = (props: StepActionFieldProps) =>
{
    const {index, step, onPatch, selectedCommunicationName, onPickCommunication, onClearCommunication} = props;
    const styles = useStepActionFieldStyles();

    return (
        <div id={"step-card-div-11"}
             className={styles.field}>
            <Text id={"step-card-text-5"}
                  size={200}
                  weight="semibold">
                {step.type === "ACTION" ? "Action" : "Message Template"}
            </Text>
            {step.type === "ACTION" ? (
                <Select id={`step-card-action-select-${index}`}
                        value={step.actionHandlerKey ?? ""}
                        onChange={(_, d) => onPatch({actionHandlerKey: d.value})}
                        size="small">
                    <option id={"step-card-option-9"}
                            value="">Select action...</option>
                    {BUILT_IN_ACTION_KEYS.map(k => (
                        <option id={`step-card-option-10-${k}`}
                                key={k}
                                value={k}>{formatTriggerName(k)}</option>
                    ))}
                </Select>
            ) : (
                <StepCommunicationRow index={index}
                                      communicationId={step.communicationId}
                                      selectedCommunicationName={selectedCommunicationName}
                                      onPick={onPickCommunication}
                                      onClear={onClearCommunication} />
            )}
        </div>
    );
};

export default StepActionField;
