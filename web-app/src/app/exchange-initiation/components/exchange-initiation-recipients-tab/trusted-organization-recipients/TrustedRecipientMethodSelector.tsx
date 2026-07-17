import React from "react";
import {Field, Radio, RadioGroup} from "@fluentui/react-components";

export type TrustedRecipientMethod = "PERSON" | "GROUP";

interface TrustedRecipientMethodSelectorProps
{
    method: TrustedRecipientMethod;
    canResolvePerson: boolean;
    canDiscoverGroups: boolean;
    onChange: (method: TrustedRecipientMethod) => void;
}

const TrustedRecipientMethodSelector: React.FC<TrustedRecipientMethodSelectorProps> = (props) =>
    <Field
        id={"trusted-recipient-method-field"}
        label={"Recipient type"}
    >
        <RadioGroup
            id={"trusted-recipient-method-group"}
            layout={"vertical"}
            value={props.method}
            onChange={(_, data) => props.onChange(data.value as TrustedRecipientMethod)}
        >
            {props.canResolvePerson && <Radio
                id={"trusted-recipient-method-person"}
                value={"PERSON"}
                label={"Person by exact email"}
            />}
            {props.canDiscoverGroups && <Radio
                id={"trusted-recipient-method-group-option"}
                value={"GROUP"}
                label={"Published group"}
            />}
        </RadioGroup>
    </Field>;

export default TrustedRecipientMethodSelector;
