import {
    Field,
    Tag,
} from '@fluentui/react-components';
import {
    TagPicker,
    TagPickerControl,
    TagPickerGroup,
    TagPickerInput,
    TagPickerList,
    TagPickerOption,
} from '@fluentui/react-tag-picker';
import {
    CONSTRAINT_OPTIONS,
    ConstraintTag,
    parseConstraintSelection,
} from './registeredPersonConstraints.ts';

interface RegisteredPersonConstraintsProps
{
    selected: ConstraintTag[];
    onChange: (selected: ConstraintTag[]) => void;
}

const RegisteredPersonConstraints = ({selected, onChange}: RegisteredPersonConstraintsProps) => (
    <Field
        id={"registered-person-constraints-field"}
        label="Access constraints"
    >
        <TagPicker
            selectedOptions={selected}
            onOptionSelect={(_event, data) => onChange(parseConstraintSelection(data.selectedOptions))}
        >
            <TagPickerControl id={"registered-person-constraints-tag-picker-control-1"}>
                <TagPickerGroup id={"registered-person-constraints-tag-picker-group-1"}
                                aria-label="Selected constraint tags">
                    {selected.map(tag => (
                        <Tag
                            id={`registered-person-constraint-${tag.toLowerCase()}`}
                            key={tag}
                            shape="circular"
                            value={tag}
                        >
                            {CONSTRAINT_OPTIONS.find(option => option.value === tag)?.label ?? tag}
                        </Tag>
                    ))}
                </TagPickerGroup>
                <TagPickerInput
                    id={"registered-person-constraints-input"}
                    aria-label="Constraint tags"
                    placeholder="Add constraints"
                />
            </TagPickerControl>
            <TagPickerList id={"registered-person-constraints-tag-picker-list-1"}>
                {CONSTRAINT_OPTIONS.filter(option => !selected.includes(option.value)).map(option => (
                    <TagPickerOption
                        id={`registered-person-constraint-option-${option.value.toLowerCase()}`}
                        key={option.value}
                        value={option.value}
                    >
                        {option.label}
                    </TagPickerOption>
                ))}
            </TagPickerList>
        </TagPicker>
    </Field>
);

export default RegisteredPersonConstraints;
