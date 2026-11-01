import {Field, Input, Textarea} from '@fluentui/react-components';
import VariableTokenInput from '../../../../../components/variable-token-input/VariableTokenInput';
import {AvailableVariablesDto} from '../../../../models/models';

interface Props
{
    fieldId: string;
    label: string;
    hint: string;
    value: string;
    onChange: (value: string) => void;
    availableVariables: AvailableVariablesDto | null;
    multiline: boolean;
    tokenPlaceholder: string;
    fallbackId: string;
    fallbackPlaceholder: string;
}

const CommunicationVariableField = (props: Props) =>
{
    const {fieldId, label, hint, value, onChange, availableVariables, multiline, tokenPlaceholder} = props;

    return (
        <Field
            id={fieldId}
            label={label}
            required
            hint={hint}
        >
            {availableVariables ? (
                <VariableTokenInput
                    value={value}
                    onChange={onChange}
                    availableVariables={availableVariables}
                    multiline={multiline}
                    placeholder={tokenPlaceholder}
                />
            ) : multiline ? (
                <Textarea
                    id={props.fallbackId}
                    value={value}
                    onChange={(_, d) => onChange(d.value)}
                    rows={6}
                    placeholder={props.fallbackPlaceholder}
                />
            ) : (
                <Input
                    id={props.fallbackId}
                    value={value}
                    onChange={(_, d) => onChange(d.value)}
                    placeholder={props.fallbackPlaceholder}
                />
            )}
        </Field>
    );
};

export default CommunicationVariableField;
