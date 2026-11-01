import {Select, Text} from "@fluentui/react-components";
import {StepOutcomeSpecDraft, WorkflowStepSpecDraft, WorkflowTriggerEventDto} from "../../../../models/models.tsx";
import {formatTriggerName} from "../../workflowUtils.ts";
import {useOutcomeFieldStyles} from "./OutcomeFieldStyles.tsx";

interface OutcomeFieldProps
{
    label: string;
    value?: StepOutcomeSpecDraft;
    steps: WorkflowStepSpecDraft[];
    triggers: WorkflowTriggerEventDto[];
    onChange: (v: StepOutcomeSpecDraft) => void;
}

const OutcomeField = ({label, value, steps, triggers, onChange}: OutcomeFieldProps) =>
{
    const styles = useOutcomeFieldStyles();
    const labelSlug = label.replace(/\s+/g, "-").toLowerCase();

    return (
        <div id={"step-card-div-1"}
             className={styles.outcomeFieldColumn}>
            <Text id={"step-card-text-1"}
                  size={200}
                  weight="semibold">{label}</Text>
            <div id={"step-card-div-2"}
                 className={styles.outcomeFieldRow}>
                <Select id={`outcome-nextstep-select-${labelSlug}`}
                        value={value?.nextStep ?? "END"}
                        onChange={(_, d) => onChange({...value, nextStep: d.value})}
                        size="small">
                    <option id={"step-card-option-1"}
                            value="END">End workflow</option>
                    {steps.map((s, i) => (
                        <option id={`step-card-option-2-${i}`}
                                key={i}
                                value={String(i)}>
                            {`Go to step ${i + 1}${s.name ? ` (${s.name})` : ""}`}
                        </option>
                    ))}
                </Select>
                <Select id={`outcome-emit-select-${labelSlug}`}
                        value={value?.emit ?? ""}
                        onChange={(_, d) => onChange({
                            ...value,
                            nextStep: value?.nextStep ?? "END",
                            emit: d.value || undefined,
                        })}
                        size="small"
                        className={styles.outcomeEmitSelect}>
                    <option id={"step-card-option-3"}
                            value="">No event emitted</option>
                    {triggers.map(t => (
                        <option id={`step-card-option-4-${t.eventName}`}
                                key={t.eventName}
                                value={t.eventName}>
                            {formatTriggerName(t.eventName)}
                        </option>
                    ))}
                </Select>
            </div>
        </div>
    );
};

export default OutcomeField;
