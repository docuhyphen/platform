import {Input, Select, Text} from "@fluentui/react-components";
import {QuorumKind, WorkflowStepSpecDraft} from "../../../../models/models.tsx";
import {useStepQuorumFieldStyles} from "./StepQuorumFieldStyles.tsx";

interface StepQuorumFieldProps
{
    index: number;
    step: WorkflowStepSpecDraft;
    onPatch: (patch: Partial<WorkflowStepSpecDraft>) => void;
}

const StepQuorumField = ({index, step, onPatch}: StepQuorumFieldProps) =>
{
    const styles = useStepQuorumFieldStyles();

    return (
        <div id={"step-card-div-9"}
             className={styles.field}>
            <Text id={"step-card-text-4"}
                  size={200}
                  weight="semibold">Quorum</Text>
            <div id={"step-card-div-10"}
                 className={styles.quorumRow}>
                <Select id={`step-card-quorum-select-${index}`}
                        value={step.quorum.kind}
                        onChange={(_, d) => onPatch({quorum: {kind: d.value as QuorumKind}})}
                        size="small">
                    <option id={"step-card-option-6"}
                            value="ANY">Any (first to approve)</option>
                    <option id={"step-card-option-7"}
                            value="ALL">All must approve</option>
                    <option id={"step-card-option-8"}
                            value="N_OF_M">N of M approvers</option>
                </Select>
                {step.quorum.kind === "N_OF_M" && (
                    <Input id={`step-card-quorum-n-input-${index}`}
                           size="small"
                           type="number"
                           className={styles.quorumNInput}
                           value={String(step.quorum.n ?? 1)}
                           onChange={(_, d) => onPatch({quorum: {...step.quorum, n: parseInt(d.value) || 1}})} />
                )}
            </div>
        </div>
    );
};

export default StepQuorumField;
