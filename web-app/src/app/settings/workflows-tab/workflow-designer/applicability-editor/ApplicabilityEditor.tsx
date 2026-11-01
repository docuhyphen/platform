import {Spinner, Text} from "@fluentui/react-components";
import {WorkflowApplicabilityDraft, WorkflowFieldConditionDraft} from "../../../../models/models.tsx";
import {useApplicabilityEditorStyles} from "./ApplicabilityEditorStyles.tsx";
import {defaultConditionFor} from "./applicabilityOperators.ts";
import ApplicabilityConditionRow from "./applicability-condition-row/ApplicabilityConditionRow.tsx";
import ApplicabilityFieldPicker from "./applicability-field-picker/ApplicabilityFieldPicker.tsx";
import ApplicabilitySchemaSelect from "./applicability-schema-select/ApplicabilitySchemaSelect.tsx";
import {useApplicabilitySchemaFields} from "./useApplicabilitySchemaFields.ts";

interface Props
{
    applicability?: WorkflowApplicabilityDraft;
    onChange: (next?: WorkflowApplicabilityDraft) => void;
}

const ApplicabilityEditor = ({applicability, onChange}: Props) =>
{
    const styles = useApplicabilityEditorStyles();
    const {schemas, selectedSchemaId, setSelectedSchemaId, fields, loadingFields} = useApplicabilitySchemaFields();

    const conditions = applicability?.fieldConditions ?? [];

    const emit = (next: WorkflowFieldConditionDraft[]) =>
        onChange(next.length > 0 ? {fieldConditions: next} : undefined);

    const bindingFor = (fieldDefinitionId: string) =>
        fields.find(f => f.fieldDefinitionId === fieldDefinitionId);

    const usedIds = new Set(conditions.map(c => c.fieldDefinitionId));
    const availableFields = fields.filter(f => !usedIds.has(f.fieldDefinitionId));

    const addField = (fieldDefinitionId: string) =>
    {
        const binding = fields.find(f => f.fieldDefinitionId === fieldDefinitionId);
        if (!binding) return;
        emit([...conditions, defaultConditionFor(binding)]);
    };

    return (
        <div id={"applicability-editor-div-1"}
             className={styles.container}>
            <div id={"applicability-editor-div-2"}
                 className={styles.header}>
                <Text id={"applicability-editor-text-1"}
                      size={200}
                      className={styles.hint}>
                    The workflow runs only when every condition matches (AND). Leave empty to always run.
                </Text>
            </div>

            <ApplicabilitySchemaSelect schemas={schemas}
                                       value={selectedSchemaId}
                                       onChange={setSelectedSchemaId}/>

            <div id={"applicability-editor-div-4"}
                 className={styles.conditionList}>
                {conditions.map((condition, i) => (
                    <ApplicabilityConditionRow
                        key={`${condition.fieldDefinitionId}-${i}`}
                        index={i}
                        condition={condition}
                        binding={bindingFor(condition.fieldDefinitionId)}
                        onChange={next => emit(conditions.map((c, idx) => (idx === i ? next : c)))}
                        onRemove={() => emit(conditions.filter((_, idx) => idx !== i))}
                    />
                ))}
                {conditions.length === 0 && (
                    <Text id={"applicability-editor-text-4"}
                          size={200}
                          className={styles.emptyText}>
                        No conditions. This workflow runs for every matching trigger.
                    </Text>
                )}
            </div>

            {loadingFields && <Spinner id={"applicability-editor-spinner-1"}
                                       size="tiny"
                                       label="Loading fields..."/>}

            {selectedSchemaId && !loadingFields && (
                <ApplicabilityFieldPicker availableFields={availableFields}
                                          onAdd={addField}/>
            )}
        </div>
    );
};

export default ApplicabilityEditor;
