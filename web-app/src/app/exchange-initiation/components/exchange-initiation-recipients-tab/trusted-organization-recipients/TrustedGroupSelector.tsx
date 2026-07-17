import React from "react";
import {Combobox, Field, Option, Spinner, Text} from "@fluentui/react-components";
import {PublishedExchangeGroup} from "../../../../../services/organizationTrust.ts";

interface TrustedGroupSelectorProps
{
    groups: PublishedExchangeGroup[];
    selectedGroupId?: string;
    organizationSelected: boolean;
    loading: boolean;
    error?: string;
    onSelect: (groupId?: string) => void;
}

const TrustedGroupSelector: React.FC<TrustedGroupSelectorProps> = (props) =>
{
    if (!props.organizationSelected)
    {
        return null;
    }
    if (props.loading)
    {
        return <Spinner
            id={"trusted-group-loading"}
            label={"Loading published groups..."}
            size={"tiny"}
        />;
    }

    const selected = props.groups.find((group) => group.id === props.selectedGroupId);
    return <Field
        id={"trusted-group-field"}
        label={"Published group"}
        validationMessage={props.error}
        validationState={props.error ? "error" : "none"}
    >
        <Combobox
            id={"trusted-group-combobox"}
            placeholder={props.groups.length === 0 ? "No published groups available" : "Select a published group"}
            value={selected?.name ?? ""}
            onOptionSelect={(_, data) => props.onSelect(data.optionValue)}
        >
            {props.groups.map((group) => <Option
                id={`trusted-group-option-${group.id}`}
                key={group.id}
                text={group.name}
                value={group.id}
            >
                <Text id={`trusted-group-option-name-${group.id}`}>{group.name}</Text>
            </Option>)}
        </Combobox>
    </Field>;
};

export default TrustedGroupSelector;
