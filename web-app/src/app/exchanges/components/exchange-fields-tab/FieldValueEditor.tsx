import {Field, Input, Textarea} from '@fluentui/react-components';
import {FieldValueType, SchemaFieldBindingDto} from '../../../models/models';
import {toCanonicalDateTime, toDateTimeInputValue} from './fieldDateTimeCanonical';
import FieldBooleanEditor from './FieldBooleanEditor';
import FieldSelectEditor from './FieldSelectEditor';

interface Props
{
    binding: SchemaFieldBindingDto;
    value: unknown;
    onChange: (value: unknown) => void;
    showLabel?: boolean;
    labelledBy?: string;
}

const FieldValueEditor = ({binding, value, onChange, showLabel = true, labelledBy}: Props) =>
{
    const id = `exchange-field-${binding.fieldContractId}`;
    const disabled = binding.isReadOnly;
    // A number is carried as text so its width survives; the editors bind to that text directly.
    const asText = value === null || value === undefined ? '' : String(value);

    const renderControl = () =>
    {
        switch (binding.valueType)
        {
            case FieldValueType.LONG_TEXT:
                return (
                    <Textarea id={id}
                              aria-labelledby={labelledBy}
                              disabled={disabled}
                              placeholder="Enter details"
                              rows={3}
                              value={typeof value === 'string' ? value : ''}
                              onChange={(_, d) => onChange(d.value)}/>
                );
            case FieldValueType.BOOLEAN:
                return (
                    <FieldBooleanEditor id={id}
                                        labelledBy={labelledBy}
                                        value={value}
                                        disabled={disabled}
                                        onChange={onChange}/>
                );
            case FieldValueType.INTEGER:
                return (
                    <Input id={id}
                           aria-labelledby={labelledBy}
                           type="number"
                           step={1}
                           disabled={disabled}
                           placeholder="Enter a whole number"
                           value={asText}
                           onKeyDown={e =>
                           {
                               if (e.key === '.' || e.key === ',') e.preventDefault();
                           }}
                           onChange={(_, d) => onChange(d.value.replace(/[.,]/g, ''))}/>
                );
            case FieldValueType.DECIMAL:
                return (
                    <Input id={id}
                           aria-labelledby={labelledBy}
                           type="number"
                           disabled={disabled}
                           placeholder="Enter a decimal number"
                           value={asText}
                           onChange={(_, d) => onChange(d.value)}/>
                );
            case FieldValueType.DATE:
                return (
                    <Input id={id}
                           aria-labelledby={labelledBy}
                           type="date"
                           disabled={disabled}
                           value={typeof value === 'string' ? value : ''}
                           onChange={(_, d) => onChange(d.value)}/>
                );
            case FieldValueType.DATE_TIME:
                return (
                    <Input id={id}
                           aria-labelledby={labelledBy}
                           type="datetime-local"
                           disabled={disabled}
                           value={toDateTimeInputValue(value)}
                           onChange={(_, d) => onChange(toCanonicalDateTime(d.value))}/>
                );
            case FieldValueType.SINGLE_SELECT:
            case FieldValueType.MULTI_SELECT:
                return (
                    <FieldSelectEditor id={id}
                                       labelledBy={labelledBy}
                                       binding={binding}
                                       value={value}
                                       disabled={disabled}
                                       onChange={onChange}/>
                );
            default:
                return (
                    <Input id={id}
                           aria-labelledby={labelledBy}
                           disabled={disabled}
                           placeholder="Enter a value"
                           value={typeof value === 'string' ? value : ''}
                           onChange={(_, d) => onChange(d.value)}/>
                );
        }
    };

    return (
        <Field label={showLabel ? binding.label : undefined}
               required={showLabel && binding.isRequired}
               hint={showLabel ? binding.helpText ?? undefined : undefined}>
            {renderControl()}
        </Field>
    );
};

export default FieldValueEditor;
