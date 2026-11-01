import {Input, Switch, Text, Textarea} from "@fluentui/react-components";
import {WorkflowDesignerState, WorkflowTriggerEventDto} from "../../../../models/models.tsx";
import WorkflowTagEditor from "./workflow-tag-editor/WorkflowTagEditor.tsx";
import WorkflowTriggerSelect from "./workflow-trigger-select/WorkflowTriggerSelect.tsx";
import {useWorkflowMetadataFormStyles} from "./WorkflowMetadataFormStyles.tsx";

interface Props
{
    state: WorkflowDesignerState;
    onPatch: (patch: Partial<WorkflowDesignerState>) => void;
    triggers: WorkflowTriggerEventDto[];
    triggersError: string | null;
    triggerDisabled: boolean;
}

const WorkflowMetadataForm = ({state, onPatch, triggers, triggersError, triggerDisabled}: Props) =>
{
    const styles = useWorkflowMetadataFormStyles();

    return (
        <div id={"workflow-metadata-form-div-1"}
             className={styles.formGrid}>
            <div id={"workflow-metadata-form-div-2"}
                 className={styles.formField}>
                <Text id={"workflow-metadata-form-text-1"}
                      size={200}
                      weight="semibold">
                    Name *
                </Text>
                <Input
                    id="workflow-designer-name-input"
                    value={state.name}
                    onChange={(_, d) => onPatch({name: d.value})}
                    placeholder="Workflow name"
                />
            </div>

            <div id={"workflow-metadata-form-div-3"}
                 className={styles.formField}>
                <Text id={"workflow-metadata-form-text-2"}
                      size={200}
                      weight="semibold">
                    Trigger Event *
                </Text>
                <WorkflowTriggerSelect
                    value={state.triggerEvent}
                    onChange={triggerEvent => onPatch({triggerEvent})}
                    triggers={triggers}
                    triggersError={triggersError}
                    disabled={triggerDisabled}
                />
            </div>

            <div id={"workflow-metadata-form-div-4"}
                 className={`${styles.formField} ${styles.fullWidth}`}>
                <Text id={"workflow-metadata-form-text-5"}
                      size={200}
                      weight="semibold">
                    Summary
                </Text>
                <Textarea
                    id="workflow-designer-summary-textarea"
                    value={state.summary}
                    onChange={(_, d) => onPatch({summary: d.value})}
                    placeholder="Brief description of what this workflow does"
                    rows={2}
                />
            </div>

            <div id={"workflow-metadata-form-div-5"}
                 className={`${styles.formField} ${styles.fullWidth}`}>
                <Text id={"workflow-metadata-form-text-6"}
                      size={200}
                      weight="semibold">
                    Tags
                </Text>
                <WorkflowTagEditor
                    tags={state.generalTags}
                    onChange={generalTags => onPatch({generalTags})}
                />
            </div>

            <div id={"workflow-metadata-form-div-7"}
                 className={styles.formField}>
                <Switch
                    id="workflow-designer-active-switch"
                    label="Active"
                    checked={state.isActive}
                    onChange={(_, d) => onPatch({isActive: d.checked})}
                />
            </div>
        </div>
    );
};

export default WorkflowMetadataForm;
