import {Field, Select} from "@fluentui/react-components";
import {ChoiceOption} from "./choiceOptions.ts";

interface ChoiceSelectProps<T extends string>
{
    id: string;
    label: string;
    value: T | "";
    options: ChoiceOption<T>[];
    disabled?: boolean;
    hint?: string;
    placeholder?: string;
    className?: string;
    onChange: (value: T) => void;
}

const ChoiceSelect = <T extends string>({
    id,
    label,
    value,
    options,
    disabled,
    hint,
    placeholder,
    className,
    onChange,
}: ChoiceSelectProps<T>) => (
    <Field id={`${id}-field`}
           label={label}
           hint={hint}
           className={className}>
        <Select id={id}
                value={value}
                disabled={disabled}
                onChange={(_, data) =>
                {
                    const chosen = options.find(option => option.value === data.value);
                    if (chosen) onChange(chosen.value);
                }}>
            {placeholder !== undefined && (
                <option id={`${id}-placeholder`}
                        value={""}>
                    {placeholder}
                </option>
            )}
            {options.map(option => (
                <option key={option.value}
                        id={`${id}-${option.value}`}
                        value={option.value}>
                    {option.label}
                </option>
            ))}
        </Select>
    </Field>
);

export default ChoiceSelect;
