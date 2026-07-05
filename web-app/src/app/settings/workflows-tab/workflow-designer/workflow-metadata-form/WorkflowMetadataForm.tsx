import {useState} from "react";
import {Button, Input, Select, Switch, Tag, Text, Textarea} from "@fluentui/react-components";
import {WorkflowDesignerState, WorkflowTriggerEventDto} from "../../../../models/models.tsx";
import {AddIcon} from "../../../../components/IconBundles.tsx";
import {formatTriggerName} from "../../workflowUtils.ts";
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
    const [tagInput, setTagInput] = useState("");

    const selectedTrigger = triggers.find(t => t.eventName === state.triggerEvent);

    const addTag = () =>
    {
        const tag = tagInput.trim();
        if (tag && !state.generalTags.includes(tag))
            onPatch({generalTags: [...state.generalTags, tag]});
        setTagInput("");
    };

    const removeTag = (tag: string) =>
        onPatch({generalTags: state.generalTags.filter(t => t !== tag)});

    return (
        <div className={styles.formGrid}>
            <div className={styles.formField}>
                <Text size={200}
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

            <div className={styles.formField}>
                <Text size={200}
                      weight="semibold">
                    Trigger Event *
                </Text>
                {triggersError ? (
                    <Text
                        size={200}
                        className={styles.triggerError}
                    >
                        {triggersError}
                    </Text>
                ) : (
                    <Select
                        id="workflow-designer-trigger-select"
                        value={state.triggerEvent}
                        onChange={(_, d) => onPatch({triggerEvent: d.value})}
                        disabled={triggerDisabled}
                    >
                        <option value="">When does this workflow run?</option>
                        {triggers.filter(t => t.isActive).map(t => (
                            <option key={t.eventName}
                                    value={t.eventName}>
                                {formatTriggerName(t.eventName)}
                            </option>
                        ))}
                    </Select>
                )}
                {selectedTrigger?.description && (
                    <Text
                        size={200}
                        className={styles.triggerDescription}
                    >
                        {selectedTrigger.description}
                    </Text>
                )}
            </div>

            <div className={`${styles.formField} ${styles.fullWidth}`}>
                <Text size={200}
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

            <div className={`${styles.formField} ${styles.fullWidth}`}>
                <Text size={200}
                      weight="semibold">
                    Tags
                </Text>
                <div className={styles.tagInput}>
                    {state.generalTags.map(tag => (
                        <Tag key={tag}
                             size="small"
                             shape={"circular"}
                             dismissible
                             onClick={() => removeTag(tag)}>
                            {tag}
                        </Tag>
                    ))}
                    <Input
                        id="workflow-designer-tag-input"
                        size="small"
                        appearance="underline"
                        placeholder="Add tag, press Enter"
                        value={tagInput}
                        onChange={(_, d) => setTagInput(d.value)}
                        onKeyDown={e => { if (e.key === "Enter") { e.preventDefault(); addTag(); } }}
                        className={styles.tagInputField}
                    />
                    <Button
                        id="workflow-designer-add-tag-btn"
                        shape="circular"
                        appearance="subtle"
                        size={"medium"}
                        icon={<AddIcon/>}
                        onClick={addTag}
                    />
                </div>
            </div>

            <div className={styles.formField}>
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
