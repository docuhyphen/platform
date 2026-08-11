import React from "react";
import {Button, Checkbox, CheckboxOnChangeData, Spinner, Text} from "@fluentui/react-components";

interface IndividualOnboardingSubmitSectionProps
{
    alsoRegisterOrganization: boolean;
    buttonWithLoadingClassName: string;
    registeringProfile: boolean;
    onRegisterIndividual: () => void;
    onRegisterOrganizationChange: (
        event: React.ChangeEvent<HTMLInputElement>,
        data: CheckboxOnChangeData,
    ) => void;
}

const IndividualOnboardingSubmitSection = ({
    alsoRegisterOrganization,
    buttonWithLoadingClassName,
    registeringProfile,
    onRegisterIndividual,
    onRegisterOrganizationChange,
}: IndividualOnboardingSubmitSectionProps) => (
    <>
        <Checkbox
            id={"individual-onboarding-register-org-checkbox"}
            label="Register your organization as well"
            checked={alsoRegisterOrganization}
            onChange={onRegisterOrganizationChange}
        />
        <Button
            id={"individual-onboarding-register-btn"}
            onClick={onRegisterIndividual}
            shape={"circular"}
            appearance={"primary"}
            className={buttonWithLoadingClassName}>
            {registeringProfile &&
                <Spinner size={"tiny"}/>}
            {!registeringProfile &&
                <Text>Register profile</Text>}
            {registeringProfile &&
                <Text>Registering profile</Text>}
        </Button>
    </>
);

export default IndividualOnboardingSubmitSection;
