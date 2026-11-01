import {Select, Text} from "@fluentui/react-components";
import {WorkflowTriggerEventDto} from "../../../../../models/models.tsx";
import {formatTriggerName} from "../../../workflowUtils.ts";
import {useWorkflowTriggerSelectStyles} from "./WorkflowTriggerSelectStyles.tsx";

interface Props
{
    value: string;
    onChange: (triggerEvent: string) => void;
    triggers: WorkflowTriggerEventDto[];
    triggersError: string | null;
    disabled: boolean;
}

const WorkflowTriggerSelect = ({value, onChange, triggers, triggersError, disabled}: Props) =>
{
    const styles = useWorkflowTriggerSelectStyles();
    const selectedTrigger = triggers.find(t => t.eventName === value);

    return (
        <>
            {triggersError ? (
                <Text
                    id={"workflow-metadata-form-text-3"}
                    size={200}
                    className={styles.triggerError}
                >
                    {triggersError}
                </Text>
            ) : (
                <Select
                    id="workflow-designer-trigger-select"
                    value={value}
                    onChange={(_, d) => onChange(d.value)}
                    disabled={disabled}
                >
                    <option id={"workflow-metadata-form-option-1"}
                            value="">When does this workflow run?</option>
                    {triggers.filter(t => t.isActive).map(t => (
                        <option id={`workflow-metadata-form-option-2-${t.eventName}`}
                                key={t.eventName}
                                value={t.eventName}>
                            {formatTriggerName(t.eventName)}
                        </option>
                    ))}
                </Select>
            )}
            {selectedTrigger?.description && (
                <Text
                    id={"workflow-metadata-form-text-4"}
                    size={200}
                    className={styles.triggerDescription}
                >
                    {selectedTrigger.description}
                </Text>
            )}
        </>
    );
};

export default WorkflowTriggerSelect;
