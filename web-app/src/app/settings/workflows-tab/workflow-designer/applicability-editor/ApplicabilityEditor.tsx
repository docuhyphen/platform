import {useEffect, useState} from "react";
import {Select, Spinner, Text} from "@fluentui/react-components";
import {
    SchemaDefinitionDto,
    SchemaFieldBindingDto,
    FieldScopeKind,
    WorkflowApplicabilityDraft,
    WorkflowFieldConditionDraft,
} from "../../../../models/models.tsx";
import {getResolvedSchema, listSchemas} from "../../../../../services/fieldsService.ts";
import {useApplicabilityEditorStyles} from "./ApplicabilityEditorStyles.tsx";
import {defaultConditionFor} from "./applicabilityOperators.ts";
import ApplicabilityConditionRow from "./ApplicabilityConditionRow.tsx";

interface Props
{
    applicability?: WorkflowApplicabilityDraft;
    onChange: (next?: WorkflowApplicabilityDraft) => void;
}

const EXCHANGE_RESOURCE = "EXCHANGE";

const ApplicabilityEditor = ({applicability, onChange}: Props) =>
{
    const styles = useApplicabilityEditorStyles();
    const [schemas, setSchemas] = useState<SchemaDefinitionDto[]>([]);
    const [selectedSchemaId, setSelectedSchemaId] = useState<string>("");
    const [fields, setFields] = useState<SchemaFieldBindingDto[]>([]);
    const [loadingFields, setLoadingFields] = useState(false);

    const conditions = applicability?.fieldConditions ?? [];

    useEffect(() =>
    {
        listSchemas()
            .then(all => setSchemas(all.filter(s =>
                s.scopeKind === FieldScopeKind.ORGANIZATION && s.targetResourceType === EXCHANGE_RESOURCE)))
            .catch(() => setSchemas([]));
    }, []);

    useEffect(() =>
    {
        if (!selectedSchemaId) { setFields([]); return; }
        setLoadingFields(true);
        getResolvedSchema(selectedSchemaId)
            .then(resolved => setFields(resolved.fields))
            .catch(() => setFields([]))
            .finally(() => setLoadingFields(false));
    }, [selectedSchemaId]);

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
        <div className={styles.container}>
            <div className={styles.header}>
                <Text size={400} weight="semibold">Applicability</Text>
                <Text size={200} className={styles.hint}>
                    The workflow runs only when every condition matches (AND). Leave empty to always run.
                </Text>
            </div>

            <div className={styles.schemaPicker}>
                <Text size={200} weight="semibold">Schema</Text>
                <Select
                    id="applicability-schema-select"
                    value={selectedSchemaId}
                    onChange={(_, d) => setSelectedSchemaId(d.value)}
                >
                    <option value="">Select a schema to add field conditions</option>
                    {schemas.map(s =>
                        <option key={s.id} value={s.id}>{s.displayName}</option>)}
                </Select>
                {schemas.length === 0 && (
                    <Text size={200} className={styles.hint}>
                        No organization Exchange schemas are available yet.
                    </Text>
                )}
            </div>

            <div className={styles.conditionList}>
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
                    <Text size={200} className={styles.emptyText}>
                        No conditions. This workflow runs for every matching trigger.
                    </Text>
                )}
            </div>

            {loadingFields && <Spinner size="tiny" label="Loading fields..."/>}

            {selectedSchemaId && !loadingFields && (
                <div className={styles.schemaPicker}>
                    <Text size={200} weight="semibold">Add condition</Text>
                    <Select
                        id="applicability-add-field-select"
                        value=""
                        onChange={(_, d) => { if (d.value) addField(d.value); }}
                        disabled={availableFields.length === 0}
                    >
                        <option value="">
                            {availableFields.length === 0 ? "All fields added" : "Choose a field"}
                        </option>
                        {availableFields.map(f =>
                            <option key={f.fieldDefinitionId} value={f.fieldDefinitionId}>{f.label}</option>)}
                    </Select>
                </div>
            )}
        </div>
    );
};

export default ApplicabilityEditor;
