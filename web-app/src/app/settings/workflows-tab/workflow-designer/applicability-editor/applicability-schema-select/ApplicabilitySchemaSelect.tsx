import {Select, Text} from "@fluentui/react-components";
import {SchemaDefinitionDto} from "../../../../../models/models.tsx";
import {useApplicabilitySchemaSelectStyles} from "./ApplicabilitySchemaSelectStyles.tsx";

interface Props
{
    schemas: SchemaDefinitionDto[];
    value: string;
    onChange: (schemaId: string) => void;
}

const ApplicabilitySchemaSelect = ({schemas, value, onChange}: Props) =>
{
    const styles = useApplicabilitySchemaSelectStyles();

    return (
        <div id={"applicability-editor-div-3"}
             className={styles.schemaPicker}>
            <Text id={"applicability-editor-text-2"}
                  size={200}
                  weight="semibold">Schema</Text>
            <Select
                id="applicability-schema-select"
                size={"small"}
                value={value}
                onChange={(_, d) => onChange(d.value)}
            >
                <option id={"applicability-editor-option-1"}
                        value="">Select a schema to add field conditions</option>
                {schemas.map(s =>
                    <option id={`applicability-editor-option-2-${s.id}`}
                            key={s.id}
                            value={s.id}>{s.displayName}</option>)}
            </Select>
            {schemas.length === 0 && (
                <Text id={"applicability-editor-text-3"}
                      size={200}
                      className={styles.hint}>
                    No organization Exchange schemas are available yet.
                </Text>
            )}
        </div>
    );
};

export default ApplicabilitySchemaSelect;
