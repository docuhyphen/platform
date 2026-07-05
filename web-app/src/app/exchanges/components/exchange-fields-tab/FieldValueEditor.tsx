import {Dropdown, Field, Input, Option, Switch, Textarea} from '@fluentui/react-components';
import {FieldValueType, SchemaFieldBindingDto} from '../../../models/models';

interface Props
{
    binding: SchemaFieldBindingDto;
    value: unknown;
    onChange: (value: unknown) => void;
    showLabel?: boolean;
}

const activeOptions = (binding: SchemaFieldBindingDto) =>
    binding.options.filter(o => o.active !== false);

const FieldValueEditor = ({binding, value, onChange, showLabel = true}: Props) =>
{
    const id = `exchange-field-${binding.fieldContractId}`;
    const disabled = binding.isReadOnly;

    const renderControl = () =>
    {
        switch (binding.valueType)
        {
            case FieldValueType.LONG_TEXT:
                return (
                    <Textarea id={id}
                               disabled={disabled}
                               placeholder="Enter details"
                               rows={3}
                               value={typeof value === 'string' ? value : ''}
                               onChange={(_, d) => onChange(d.value)}/>
                );
            case FieldValueType.BOOLEAN:
                return (
                    <Switch id={id}
                            disabled={disabled}
                            checked={value === true}
                            onChange={(_, d) => onChange(d.checked)}/>
                );
            case FieldValueType.INTEGER:
                return (
                    <Input id={id}
                           type="number"
                           step={1}
                           disabled={disabled}
                           placeholder="Enter a whole number"
                           value={value === null || value === undefined ? '' : String(value)}
                           onKeyDown={e =>
                           {
                               if (e.key === '.' || e.key === ',') e.preventDefault();
                           }}
                           onChange={(_, d) =>
                           {
                               const stripped = d.value.replace(/[.,]/g, '');
                               onChange(stripped);
                           }}/>
                );
            case FieldValueType.DECIMAL:
                return (
                    <Input id={id}
                           type="number"
                           disabled={disabled}
                           placeholder="Enter a decimal number"
                           value={value === null || value === undefined ? '' : String(value)}
                           onChange={(_, d) => onChange(d.value)}/>
                );
            case FieldValueType.DATE:
                return (
                    <Input id={id}
                           type="date"
                           disabled={disabled}
                           value={typeof value === 'string' ? value : ''}
                           onChange={(_, d) => onChange(d.value)}/>
                );
            case FieldValueType.DATE_TIME:
                return (
                    <Input id={id}
                           type="datetime-local"
                           disabled={disabled}
                           value={typeof value === 'string' ? value : ''}
                           onChange={(_, d) => onChange(d.value)}/>
                );
            case FieldValueType.SINGLE_SELECT:
                return (
                    <Dropdown id={id}
                               disabled={disabled}
                               placeholder="Select an option"
                               selectedOptions={value ? [String(value)] : []}
                               value={activeOptions(binding).find(o => o.code === value)?.label ?? ''}
                              onOptionSelect={(_, d) => onChange(d.optionValue)}>
                        {activeOptions(binding).map(option => (
                            <Option key={option.code}
                                    value={option.code}>
                                {option.label}
                            </Option>
                        ))}
                    </Dropdown>
                );
            case FieldValueType.MULTI_SELECT:
            {
                const selected = Array.isArray(value) ? value.map(String) : [];
                return (
                    <Dropdown id={id}
                               multiselect
                               disabled={disabled}
                               placeholder="Select one or more options"
                               selectedOptions={selected}
                               value={activeOptions(binding)
                                  .filter(o => selected.includes(o.code))
                                  .map(o => o.label)
                                  .join(', ')}
                              onOptionSelect={(_, d) => onChange(d.selectedOptions)}>
                        {activeOptions(binding).map(option => (
                            <Option key={option.code}
                                    value={option.code}>
                                {option.label}
                            </Option>
                        ))}
                    </Dropdown>
                );
            }
            default:
                return (
                    <Input id={id}
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
