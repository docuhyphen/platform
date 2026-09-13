import {Dropdown, Option} from '@fluentui/react-components';
import {FieldValueType, SchemaFieldBindingDto} from '../../../models/models';

interface Props
{
    id: string;
    binding: SchemaFieldBindingDto;
    value: unknown;
    disabled?: boolean;
    onChange: (value: unknown) => void;
}

/** A retired option keeps any value already recorded against it but is no longer selectable. */
const activeOptions = (binding: SchemaFieldBindingDto) =>
    binding.options.filter(option => option.active !== false);

const FieldSelectEditor = ({id, binding, value, disabled, onChange}: Props) =>
{
    const options = activeOptions(binding);

    if (binding.valueType === FieldValueType.MULTI_SELECT)
    {
        const selected = Array.isArray(value) ? value.map(String) : [];

        return (
            <Dropdown id={id}
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
                    <Option key={option.code}
                            value={option.code}>
                        {option.label}
                    </Option>
                ))}
            </Dropdown>
        );
    }

    return (
        <Dropdown id={id}
                  disabled={disabled}
                  placeholder="Select an option"
                  selectedOptions={value ? [String(value)] : []}
                  value={options.find(option => option.code === value)?.label ?? ''}
                  onOptionSelect={(_, data) => onChange(data.optionValue)}>
            {options.map(option => (
                <Option key={option.code}
                        value={option.code}>
                    {option.label}
                </Option>
            ))}
        </Dropdown>
    );
};

export default FieldSelectEditor;
