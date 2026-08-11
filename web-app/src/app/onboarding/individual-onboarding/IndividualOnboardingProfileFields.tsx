import {ChangeEvent} from "react";
import {Field, Input, InputOnChangeData} from "@fluentui/react-components";

interface IndividualOnboardingProfileFieldsProps
{
    firstName: string;
    lastName: string;
    onFirstNameChange: (event: ChangeEvent<HTMLInputElement>, data: InputOnChangeData) => void;
    onLastNameChange: (event: ChangeEvent<HTMLInputElement>, data: InputOnChangeData) => void;
}

const IndividualOnboardingProfileFields = ({
    firstName,
    lastName,
    onFirstNameChange,
    onLastNameChange,
}: IndividualOnboardingProfileFieldsProps) => (
    <>
        <Field
            id={"individual-onboarding-first-name-field"}
            label={"First Name"}
            validationState={"none"}
            validationMessage={""}>
            <Input
                id={"individual-onboarding-first-name-input"}
                type="text"
                maxLength={60}
                value={firstName}
                onChange={onFirstNameChange}
            />
        </Field>
        <Field
            id={"individual-onboarding-last-name-field"}
            label={"Last Name"}
            validationState={"none"}
            validationMessage={""}>
            <Input
                id={"individual-onboarding-last-name-input"}
                type="text"
                maxLength={60}
                value={lastName}
                onChange={onLastNameChange}
            />
        </Field>
    </>
);

export default IndividualOnboardingProfileFields;
