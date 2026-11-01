import {Dropdown, Option} from '@fluentui/react-components';

interface Props
{
    id: string;
    value: unknown;
    disabled?: boolean;
    labelledBy?: string;
    onChange: (value: boolean | null) => void;
}

const YES = 'true';
const NO = 'false';

const FieldBooleanEditor = ({id, value, disabled, labelledBy, onChange}: Props) =>
{
    const answered = value === true || value === false;

    return (
        <Dropdown id={id}
                  aria-labelledby={labelledBy}
                  clearable
                  disabled={disabled}
                  placeholder="Not answered"
                  selectedOptions={answered ? [value === true ? YES : NO] : []}
                  value={answered ? (value === true ? 'Yes' : 'No') : undefined}
                  onOptionSelect={(_, data) =>
                      onChange(data.optionValue === undefined ? null : data.optionValue === YES)}>
            <Option id={"field-boolean-editor-option-1"}
                    value={YES}>Yes</Option>
            <Option id={"field-boolean-editor-option-2"}
                    value={NO}>No</Option>
        </Dropdown>
    );
};

export default FieldBooleanEditor;
