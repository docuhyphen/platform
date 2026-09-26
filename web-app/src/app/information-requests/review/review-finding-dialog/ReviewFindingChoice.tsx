import {Dropdown, Field, Option} from "@fluentui/react-components";

interface Props<T extends string>
{
    id: string;
    label: string;
    options: T[];
    labelOf: (value: T) => string;
    value: T;
    disabled: boolean;
    onChange: (value: T) => void;
}

const ReviewFindingChoice = <T extends string>({id, label, options, labelOf, value, disabled, onChange}: Props<T>) => (
    <Field id={`${id}-field`}
           label={label}>
        <Dropdown id={id}
                  value={labelOf(value)}
                  selectedOptions={[value]}
                  disabled={disabled}
                  onOptionSelect={(_, data) =>
                  {
                      const chosen = options.find(option => option === data.optionValue);
                      if (chosen) onChange(chosen);
                  }}>
            {options.map(option => (
                <Option id={`${id}-${option}`}
                        key={option}
                        value={option}
                        text={labelOf(option)}>
                    {labelOf(option)}
                </Option>
            ))}
        </Dropdown>
    </Field>
);

export default ReviewFindingChoice;
