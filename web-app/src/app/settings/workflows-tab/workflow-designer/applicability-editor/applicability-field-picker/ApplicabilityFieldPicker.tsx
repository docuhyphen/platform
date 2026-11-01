import {Select, Text} from "@fluentui/react-components";
import {SchemaFieldBindingDto} from "../../../../../models/models.tsx";
import {useApplicabilityFieldPickerStyles} from "./ApplicabilityFieldPickerStyles.tsx";

interface Props
{
    availableFields: SchemaFieldBindingDto[];
    onAdd: (fieldDefinitionId: string) => void;
}

const ApplicabilityFieldPicker = ({availableFields, onAdd}: Props) =>
{
    const styles = useApplicabilityFieldPickerStyles();

    return (
        <div id={"applicability-editor-div-5"}
             className={styles.schemaPicker}>
            <Text id={"applicability-editor-text-5"}
                  size={200}
                  weight="semibold">Add condition</Text>
            <Select
                size={"small"}
                id="applicability-add-field-select"
                value=""
                onChange={(_, d) => { if (d.value) onAdd(d.value); }}
                disabled={availableFields.length === 0}
            >
                <option id={"applicability-editor-option-3"}
                        value="">
                    {availableFields.length === 0 ? "All fields added" : "Choose a field"}
                </option>
                {availableFields.map(f =>
                    <option id={`applicability-editor-option-4-${f.fieldDefinitionId}`}
                            key={f.fieldDefinitionId}
                            value={f.fieldDefinitionId}>{f.label}</option>)}
            </Select>
        </div>
    );
};

export default ApplicabilityFieldPicker;
