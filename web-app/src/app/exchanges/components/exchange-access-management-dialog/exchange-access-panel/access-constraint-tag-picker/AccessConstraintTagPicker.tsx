import React from 'react';
import {Field, Tag} from '@fluentui/react-components';
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
    labelsForTags,
    parseTagPickerSelection,
} from '../accessEntryConstraints.ts';

interface AccessConstraintTagPickerProps
{
    tags: ConstraintTag[];
    disabled: boolean;
    onChange: (tags: ConstraintTag[]) => void;
}

const AccessConstraintTagPicker: React.FC<AccessConstraintTagPickerProps> = ({tags, disabled, onChange}) => (
    <Field id={"exchange-access-panel-field-1"}
           label="Access constraints">
        <TagPicker
            selectedOptions={tags}
            disabled={disabled}
            onOptionSelect={(_e, data) => onChange(parseTagPickerSelection(data.selectedOptions))}
        >
            <TagPickerControl id={"exchange-access-panel-tag-picker-control-1"}>
                <TagPickerGroup id={"exchange-access-panel-tag-picker-group-1"}
                                aria-label="Selected constraint tags">
                    {tags.map((tag) => (
                        <Tag id={`exchange-access-panel-tag-1-${tag}`}
                             key={tag}
                             shape={"circular"}
                             value={tag}>{labelsForTags([tag])[0]}</Tag>
                    ))}
                </TagPickerGroup>
                <TagPickerInput id={"exchange-access-panel-tag-picker-input-1"}
                                aria-label="Constraint tags"/>
            </TagPickerControl>
            <TagPickerList id={"exchange-access-panel-tag-picker-list-1"}>
                {CONSTRAINT_OPTIONS
                    .filter((option) => !tags.includes(option.value))
                    .map((option) => (
                        <TagPickerOption id={`exchange-access-panel-tag-picker-option-1-${option.value}`}
                                         key={option.value}
                                         value={option.value}>
                            {option.label}
                        </TagPickerOption>
                    ))}
            </TagPickerList>
        </TagPicker>
    </Field>
);

export default AccessConstraintTagPicker;
