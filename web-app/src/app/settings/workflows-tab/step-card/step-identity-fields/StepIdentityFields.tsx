import {Input, Select, Text} from "@fluentui/react-components";
import {WorkflowStepSpecDraft, WorkflowStepType} from "../../../../models/models.tsx";
import {STEP_TYPE_LABELS} from "../stepCardOptions.ts";
import {useStepIdentityFieldsStyles} from "./StepIdentityFieldsStyles.tsx";

interface StepIdentityFieldsProps
{
    index: number;
    step: WorkflowStepSpecDraft;
    onPatch: (patch: Partial<WorkflowStepSpecDraft>) => void;
}

const StepIdentityFields = ({index, step, onPatch}: StepIdentityFieldsProps) =>
{
    const styles = useStepIdentityFieldsStyles();

    return (
        <>
            <div id={"step-card-div-7"}
                 className={`${styles.field} ${styles.fullWidth}`}>
                <Text id={"step-card-text-2"}
                      size={200}
                      weight="semibold">Step Name</Text>
                <Input id={`step-card-name-input-${index}`}
                       size="small"
                       placeholder="e.g. Manager Approval"
                       value={step.name ?? ""}
                       onChange={(_, d) => onPatch({name: d.value || undefined})} />
            </div>

            <div id={"step-card-div-8"}
                 className={styles.field}>
                <Text id={"step-card-text-3"}
                      size={200}
                      weight="semibold">Step Type</Text>
                <Select id={`step-card-type-select-${index}`}
                        value={step.type}
                        onChange={(_, d) => onPatch({type: d.value as WorkflowStepType})}
                        size="small">
                    {(Object.keys(STEP_TYPE_LABELS) as WorkflowStepType[]).map(t => (
                        <option id={`step-card-option-5-${t}`}
                                key={t}
                                value={t}>{STEP_TYPE_LABELS[t]}</option>
                    ))}
                </Select>
            </div>
        </>
    );
};

export default StepIdentityFields;
