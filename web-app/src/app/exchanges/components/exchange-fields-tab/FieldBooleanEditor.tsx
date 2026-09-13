import {Dropdown, Option} from '@fluentui/react-components';

interface Props
{
    id: string;
    value: unknown;
    disabled?: boolean;
    onChange: (value: boolean | null) => void;
}

const YES = 'true';
const NO = 'false';

/**
 * A yes or no field has three readings: yes, no, and not answered. A responder who has not reached
 * the field yet must not be recorded as having answered No, and one who did answer No must be able
 * to take that answer back, so the control offers both answers and a way to clear them.
 */
const FieldBooleanEditor = ({id, value, disabled, onChange}: Props) =>
{
    const answered = value === true || value === false;

    return (
        <Dropdown id={id}
                  clearable
                  disabled={disabled}
                  placeholder="Not answered"
                  selectedOptions={answered ? [value === true ? YES : NO] : []}
                  // Left undefined while unanswered so the placeholder shows instead of blank text.
                  value={answered ? (value === true ? 'Yes' : 'No') : undefined}
                  onOptionSelect={(_, data) =>
                      onChange(data.optionValue === undefined ? null : data.optionValue === YES)}>
            <Option value={YES}>Yes</Option>
            <Option value={NO}>No</Option>
        </Dropdown>
    );
};

export default FieldBooleanEditor;
