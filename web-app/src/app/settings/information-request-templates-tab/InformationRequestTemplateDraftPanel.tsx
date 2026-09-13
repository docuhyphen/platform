import {Button, Field, Input, Select, Text} from "@fluentui/react-components";
import {useMemo, useState} from "react";
import {InformationRequestTemplateDto, SchemaDefinitionDto} from "../../models/models.tsx";
import {DraftSaveRequest} from "./informationRequestTemplateDraftUtils.ts";
import {useInformationRequestTemplatesTabStyles} from "./InformationRequestTemplatesTabStyles.tsx";
import UnsupportedPolicyControls from "./UnsupportedPolicyControls.tsx";
interface Props
{
    template: InformationRequestTemplateDto | null;
    schemas: SchemaDefinitionDto[];
    onSave: (template: InformationRequestTemplateDto, request: DraftSaveRequest) => Promise<void>;
    onPublish: (template: InformationRequestTemplateDto) => Promise<void>;
}
const InformationRequestTemplateDraftPanel = ({template, schemas, onSave, onPublish}: Props) =>
{
    const styles = useInformationRequestTemplatesTabStyles();
    const requestSchemas = schemas.filter(schema =>
        schema.targetResourceType === "INFORMATION_REQUEST" && !!schema.latestPublishedVersion);
    const [schemaId, setSchemaId] = useState("");
    const [fieldId, setFieldId] = useState("");
    const [requirementKey, setRequirementKey] = useState("");
    const [prompt, setPrompt] = useState("");
    const selectedSchema = requestSchemas.find(schema => schema.id === (schemaId || requestSchemas[0]?.id));
    const selectedVersion = selectedSchema?.latestPublishedVersion;
    const fieldOptions = useMemo(() => selectedVersion?.bindings ?? [], [selectedVersion?.bindings]);
    const selectedFieldId = fieldId || fieldOptions[0]?.fieldDefinitionId || "";
    if (!template)
    {
        return (
            <div
                id={"information-request-template-draft-panel"}
                className={styles.panel}
            >
                <Text
                    id={"information-request-template-draft-empty"}
                    className={styles.secondaryText}
                >
                    Select a draft Template to edit.
                </Text>
            </div>
        );
    }
    const save = () => void onSave(template, {
        schemaVersionId: selectedVersion?.id ?? "",
        sectionKey: "requested-data",
        sectionTitle: "Requested data",
        requirementKey,
        prompt,
        collectedFieldDefinitionId: selectedFieldId,
    });
    return (
        <div
            id={"information-request-template-draft-panel"}
            className={styles.panel}
        >
            <Text
                id={"information-request-template-draft-title"}
                weight={"semibold"}
            >
                {template.displayName}
            </Text>
            <div className={styles.formGrid}>
                <Field
                    id={"information-request-template-schema-field"}
                    label={"Request Schema"}
                >
                    <Select
                        id={"information-request-template-schema-select"}
                        value={selectedSchema?.id ?? ""}
                        onChange={event => setSchemaId(event.target.value)}
                    >
                        {requestSchemas.map(schema => (
                            <option
                                key={schema.id}
                                value={schema.id}
                            >
                                {schema.displayName}
                            </option>
                        ))}
                    </Select>
                </Field>
                <Field
                    id={"information-request-template-collected-field"}
                    label={"Collected Field"}
                >
                    <Select
                        id={"information-request-template-field-select"}
                        value={selectedFieldId}
                        onChange={event => setFieldId(event.target.value)}
                    >
                        {fieldOptions.map(field => (
                            <option
                                key={field.fieldDefinitionId}
                                value={field.fieldDefinitionId}
                            >
                                {field.label}
                            </option>
                        ))}
                    </Select>
                </Field>
                <Field
                    id={"information-request-template-requirement-key-field"}
                    label={"Requirement Key"}
                >
                    <Input
                        id={"information-request-template-requirement-key-input"}
                        value={requirementKey}
                        onChange={(_, data) => setRequirementKey(data.value)}
                    />
                </Field>
                <Field
                    id={"information-request-template-prompt-field"}
                    label={"Prompt"}
                    className={styles.fullWidth}
                >
                    <Input
                        id={"information-request-template-requirement-prompt-input"}
                        value={prompt}
                        onChange={(_, data) => setPrompt(data.value)}
                    />
                </Field>
            </div>
            <UnsupportedPolicyControls controls={template.unsupportedPolicyControls ?? []}/>
            <div className={styles.actionRow}>
                <Button
                    id={"information-request-template-save-draft"}
                    appearance={"primary"}
                    shape={"circular"}
                    disabled={!selectedVersion || !selectedFieldId || !requirementKey.trim() || !prompt.trim()}
                    onClick={save}
                >
                    Save draft
                </Button>
                <Button
                    id={"information-request-template-publish-draft"}
                    appearance={"subtle"}
                    shape={"circular"}
                    disabled={!template.draftVersion}
                    onClick={() => void onPublish(template)}
                >
                    Publish draft
                </Button>
            </div>
        </div>
    );
};
export default InformationRequestTemplateDraftPanel;
