import {Dropdown, Option} from '@fluentui/react-components';
import {FieldValueType, SchemaFieldBindingDto} from '../../../models/models';

interface Props
{
    id: string;
    binding: SchemaFieldBindingDto;
    value: unknown;
    disabled?: boolean;
    labelledBy?: string;
    onChange: (value: unknown) => void;
}

const activeOptions = (binding: SchemaFieldBindingDto) =>
    binding.options.filter(option => option.active !== false);

const FieldSelectEditor = ({id, binding, value, disabled, labelledBy, onChange}: Props) =>
{
    const options = activeOptions(binding);

    if (binding.valueType === FieldValueType.MULTI_SELECT)
    {
        const selected = Array.isArray(value) ? value.map(String) : [];

        return (
            <Dropdown id={id}
                      aria-labelledby={labelledBy}
                      multiselect
                      disabled={disabled}
                      placeholder="Select one or more options"
                      selectedOptions={selected}
                      value={options
                          .filter(option => selected.includes(option.code))
                          .map(option => option.label)
                          .join(', ')}
                      onOptionSelect={(_, data) => onChange(data.selectedOptions)}>
                {options.map(option => (
                    <Option id={`${id}-option-${option.code}`}
                            key={option.code}
                            value={option.code}>
                        {option.label}
                    </Option>
                ))}
            </Dropdown>
        );
    }

    return (
        <Dropdown id={id}
                  aria-labelledby={labelledBy}
                  clearable
                  disabled={disabled}
                  placeholder="Select an option"
                  selectedOptions={value ? [String(value)] : []}
                  value={options.find(option => option.code === value)?.label ?? ''}
                  onOptionSelect={(_, data) => onChange(data.optionValue ?? null)}>
            {options.map(option => (
                <Option id={`${id}-option-${option.code}`}
                        key={option.code}
                        value={option.code}>
                    {option.label}
                </Option>
            ))}
        </Dropdown>
    );
};

export default FieldSelectEditor;
