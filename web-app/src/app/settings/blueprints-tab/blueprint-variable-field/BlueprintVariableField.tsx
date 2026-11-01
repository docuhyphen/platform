import {Field, Input, Textarea} from '@fluentui/react-components';
import {AvailableVariablesDto} from '../../../models/models.tsx';
import VariableTokenInput from '../../../../components/variable-token-input/VariableTokenInput.tsx';

interface BlueprintVariableFieldProps
{
    fieldId: string;
    label: string;
    hint: string;
    value: string;
    onChange: (value: string) => void;
    availableVariables: AvailableVariablesDto | null;
    variablePlaceholder: string;
    plainPlaceholder: string;
    plainInputId: string;
    rows?: number;
}

const BlueprintVariableField = (
    {
        fieldId,
        label,
        hint,
        value,
        onChange,
        availableVariables,
        variablePlaceholder,
        plainPlaceholder,
        plainInputId,
        rows,
    }: BlueprintVariableFieldProps) =>
{
    const multiline = rows !== undefined;

    return (
        <Field id={fieldId}
               label={label}
               hint={hint}>
            {availableVariables ? (
                <VariableTokenInput
                    value={value}
                    onChange={onChange}
                    availableVariables={availableVariables}
                    multiline={multiline}
                    placeholder={variablePlaceholder}
                />
            ) : multiline ? (
                <Textarea
                    id={plainInputId}
                    value={value}
                    onChange={(_, d) => onChange(d.value)}
                    rows={rows}
                    placeholder={plainPlaceholder}
                />
            ) : (
                <Input
                    id={plainInputId}
                    value={value}
                    onChange={(_, d) => onChange(d.value)}
                    placeholder={plainPlaceholder}
                />
            )}
        </Field>
    );
};

export default BlueprintVariableField;
