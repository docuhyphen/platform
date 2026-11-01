import React from 'react';
import {Button} from "@fluentui/react-components";
import {OrganizationBasicDto} from "../../models/models.tsx";
import {useOrganizationOnboardingForm} from './OrganizationOnboardingFormStyles.tsx';
import {useOrganizationRegistration} from "./useOrganizationRegistration.ts";
import OrganizationOnboardingErrorMessage
    from "./organization-onboarding-error-message/OrganizationOnboardingErrorMessage.tsx";
import OrganizationOnboardingFields from "./organization-onboarding-fields/OrganizationOnboardingFields.tsx";
import OrganizationOnboardingRegisterButton
    from "./organization-onboarding-register-button/OrganizationOnboardingRegisterButton.tsx";

interface OrganizationOnboardingFormProps
{
    isOnDialog?: boolean;
    onCancel?: () => void;
    onOrganizationRegistered?: (organization: OrganizationBasicDto) => void;
}

const OrganizationOnboardingForm: React.FC<OrganizationOnboardingFormProps> = (
    {
        isOnDialog,
        onCancel,
        onOrganizationRegistered
    }
) =>
{
    const registration = useOrganizationRegistration(onOrganizationRegistered);
    const styles = useOrganizationOnboardingForm();

    const renderRegisterButton = () => (
        <OrganizationOnboardingRegisterButton registering={registration.registeringOrg}
                                              onClick={registration.onRegisterOrganization}/>
    );

    return (
        <>
            {(!registration.appUserPersonOrganization) &&
                <div id={"organization-onboarding-form-div-1"}
                     className={styles.container}>
                    <OrganizationOnboardingErrorMessage message={registration.errorMessage}
                                                        onDismiss={registration.onDismissError}/>
                    <OrganizationOnboardingFields registration={registration}/>
                    {!isOnDialog && renderRegisterButton()}
                    {isOnDialog &&
                        <div id={"organization-onboarding-form-div-3"}
                             className={styles.dialogActions}>
                            {!registration.orgRegistered && renderRegisterButton()}
                            <Button id={"org-onboarding-dialog-cancel-btn"}
                                    appearance={"secondary"}
                                    shape="circular"
                                    onClick={onCancel}>
                                {registration.orgRegistered && "Close"}
                                {!registration.orgRegistered && "Cancel"}
                            </Button>
                        </div>
                    }
                </div>
            }

            {registration.appUserPersonOrganization &&
                <div id={"organization-onboarding-form-div-4"}
                     className={styles.container}>
                    {isOnDialog &&
                        <div id={"organization-onboarding-form-div-5"}
                             className={styles.dialogActions}>
                            <Button id={"org-onboarding-dialog-close-btn"}
                                    appearance={"secondary"}
                                    shape="circular"
                                    onClick={onCancel}>
                                {registration.orgRegistered && "Close"}
                                {!registration.orgRegistered && "Cancel"}
                            </Button>
                        </div>
                    }
                </div>
            }
        </>
    );
};

export default OrganizationOnboardingForm;
