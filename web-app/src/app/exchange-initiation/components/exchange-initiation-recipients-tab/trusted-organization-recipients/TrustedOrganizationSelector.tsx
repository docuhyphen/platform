import React from "react";
import {Combobox, Field, Option, Spinner} from "@fluentui/react-components";
import {OrganizationTrustRelationship} from "../../../../../services/organizationTrust.ts";

interface TrustedOrganizationSelectorProps
{
    relationships: OrganizationTrustRelationship[];
    selectedRelationshipId?: string;
    loading: boolean;
    onSelect: (relationshipId?: string) => void;
}

const TrustedOrganizationSelector: React.FC<TrustedOrganizationSelectorProps> = (props) =>
{
    const selected = props.relationships.find((relationship) => relationship.id === props.selectedRelationshipId);

    if (props.loading)
    {
        return <Spinner
            id={"trusted-organization-loading"}
            label={"Loading Trusted Organizations..."}
            size={"tiny"}
        />;
    }

    return <Field
        id={"trusted-organization-field"}
        label={"Trusted Organization"}
    >
        <Combobox
            id={"trusted-organization-combobox"}
            placeholder={"Select a Trusted Organization"}
            value={selected?.partnerOrganizationName ?? ""}
            onOptionSelect={(_, data) => props.onSelect(data.optionValue)}
        >
            {props.relationships.map((relationship) => <Option
                id={`trusted-organization-option-${relationship.partnerOrganizationId}`}
                key={relationship.id}
                text={relationship.partnerOrganizationName}
                value={relationship.id}
            >
                {relationship.partnerOrganizationName}
            </Option>)}
        </Combobox>
    </Field>;
};

export default TrustedOrganizationSelector;
