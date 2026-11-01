import {Checkbox, Field, Input} from "@fluentui/react-components";
import {OrganizationRegistrationState} from "../useOrganizationRegistration.ts";

interface OrganizationOnboardingFieldsProps
{
    registration: OrganizationRegistrationState;
}

const OrganizationOnboardingFields = ({registration}: OrganizationOnboardingFieldsProps) =>
{
    return (
        <>
            <Field
                id={"organization-onboarding-form-field-1"}
                label={"Your organization name"}
                required
                validationState={"none"}
                validationMessage={""}>
                <Input id={"org-onboarding-name-input"}
                       type="text"
                       maxLength={120}
                       value={registration.organizationName}
                       onChange={registration.onOrganizationNameChange}/>
            </Field>

            <Field
                id={"organization-onboarding-form-field-2"}
                label={"Registration Number"}
                required
                validationState={"none"}
                validationMessage={""}>
                <Input id={"org-onboarding-registration-number-input"}
                       type="text"
                       maxLength={30}
                       value={registration.registrationNumber}
                       onChange={registration.onRegistrationNumberChange}/>
            </Field>

            <Field
                id={"organization-onboarding-form-field-3"}
                label={"Phone Number"}
                validationState={"none"}
                validationMessage={""}>
                <Input id={"org-onboarding-phone-input"}
                       type="text"
                       maxLength={30}
                       value={registration.organizationPhone}
                       onChange={registration.onOrganizationPhoneChange}/>
            </Field>

            <div id={"organization-onboarding-form-div-2"}>
                <Field
                    id={"organization-onboarding-form-field-4"}
                    label={"Email"}
                    validationState={"none"}
                    validationMessage={""}>
                    <Input id={"org-onboarding-email-input"}
                           type="text"
                           maxLength={120}
                           value={registration.organizationEmail}
                           onChange={registration.onOrganizationEmailChange}/>
                </Field>
                <Checkbox id={"org-onboarding-use-account-email-checkbox"}
                          label={"Use my account sign in email"}
                          onChange={registration.onUseAppUserEmailCheck}/>
            </div>
        </>
    );
};

export default OrganizationOnboardingFields;
