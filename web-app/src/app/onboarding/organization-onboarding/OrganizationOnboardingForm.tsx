import React, {ChangeEvent, useState} from 'react';
import {
    Button,
    Checkbox,
    Field,
    Input,
    InputOnChangeData,
    MessageBar,
    MessageBarActions,
    MessageBarBody,
    Spinner
} from "@fluentui/react-components";
import {DismissRegular} from "@fluentui/react-icons";
import {fetchAppUser, registerOrganization} from "../../../services/appUserApi.ts";
import useToken from "../../../context/useToken.tsx";
import {OrganizationBasicDto, ResponseError} from "../../models/models.tsx";
import {useNavigate} from "react-router-dom";
import {useAuth} from "../../../context/AuthContext.tsx";
import {useOrganizationOnboardingForm} from './OrganizationOnboardingFormStyles.tsx';
import {useGlobalStyles} from "../../../GlobalStyles.tsx";
import validator from 'validator';


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
    const [organizationName, setOrganizationName] = useState('');
    const [organizationEmail, setOrganizationEmail] = useState('');
    const [registrationNumber, setRegistrationNumber] = useState('');
    const [organizationPhone, setOrganizationPhone] = useState('');
    const [registeringOrg, setRegisteringOrg] = useState(false);
    const [orgRegistered, setOrgRegistered] = useState(false);
    const [errorMessage, setErrorMessage] = useState<string | undefined>();
    const token = useToken();
    const navigate = useNavigate();
    const {setAppUserPersonOrganization, appUserPersonOrganization, appUser, setAppUser, token: authToken} = useAuth();
    const styles = useOrganizationOnboardingForm();
    const globalStyles = useGlobalStyles();

    const validateInputs = (): boolean =>
    {
        if (!organizationName.trim())
        {
            setErrorMessage("Organization name is required.");
            return false;
        }
        if (!registrationNumber.trim())
        {
            setErrorMessage("Registration number is required.");
            return false;
        }
        if (organizationEmail.trim() && !validator.isEmail(organizationEmail.trim()))
        {
            setErrorMessage("Please enter a valid organization email.");
            return false;
        }
        if (organizationPhone.trim() && !validator.isMobilePhone(organizationPhone.trim(), 'any', {strictMode: false}))
        {
            setErrorMessage("Please enter a valid phone number.");
            return false;
        }
        return true;
    };

    const onRegisterOrganization = async () =>
    {
        if (registeringOrg) return;

        setErrorMessage(undefined);

        if (!validateInputs())
        {
            return;
        }

        setRegisteringOrg(true);

        try
        {
            const organization = {
                name: organizationName.trim(),
                registrationNumber: registrationNumber.trim(),
                email: organizationEmail.trim(),
                phoneNumber: organizationPhone.trim(),
            };
            const registeredOrganization: OrganizationBasicDto = await registerOrganization(organization, token);

            setAppUserPersonOrganization(registeredOrganization);
            setOrgRegistered(true);

            // Refetch appUser to pick up the updated role (e.g. ORG_ADMIN)
            try
            {
                const updatedUser = await fetchAppUser(authToken);
                setAppUser(updatedUser);
            }
            catch (e)
            {
                console.warn("Failed to refresh user after org registration", e);
            }

            if (onOrganizationRegistered)
            {
                onOrganizationRegistered(registeredOrganization);
            }
        }
        catch (error)
        {
            console.error('Registration failed', error);
            setErrorMessage((error as ResponseError)?.errorMessage || "Failed to register organization. Please try again.");
        }
        finally
        {
            setRegisteringOrg(false);
        }
    };

    const renderErrorMessage = () => (
        errorMessage && (
            <MessageBar intent={"error"}>
                <MessageBarBody>
                    {errorMessage}
                </MessageBarBody>
                <MessageBarActions
                    containerAction={
                        <Button
                            id={"org-onboarding-dismiss-error-btn"}
                            onClick={() => setErrorMessage(undefined)}
                            appearance="transparent"
                            shape={"circular"}
                            icon={<DismissRegular/>}
                        />
                    }
                />
            </MessageBar>
        )
    );

    const onOrganizationNameChange = (_e: ChangeEvent<HTMLInputElement>, newValue: InputOnChangeData) =>
    {
        setOrganizationName(newValue.value || '');
    }

    const onRegistrationNumberChange = (_e: ChangeEvent<HTMLInputElement>, newValue: InputOnChangeData) =>
    {
        setRegistrationNumber(newValue.value || '');
    }

    const onOrganizationEmailChange = (_e: ChangeEvent<HTMLInputElement>, newValue: InputOnChangeData) =>
    {
        setOrganizationEmail(newValue.value || '');
    }

    const onOrganizationPhoneChange = (_e: ChangeEvent<HTMLInputElement>, newValue: InputOnChangeData) =>
    {
        setOrganizationPhone(newValue.value || '');
    }

    const onUseAppUserEmailCheck = (ev: React.FormEvent<HTMLInputElement>, data) =>
    {
        if (data.checked)
        {
            setOrganizationEmail(appUser.email);
        }
        else
        {
            setOrganizationEmail('');
        }
    }

    const renderRegisterButton = () =>
    {
        return (
            <Button id={"org-onboarding-register-btn"}
                    appearance={"primary"}
                    shape={"circular"}
                    className={globalStyles.buttonWithLoading}
                    onClick={onRegisterOrganization}>
                {registeringOrg && <>
                    <Spinner size={"tiny"}/>
                    Register
                </>
                }
                {!registeringOrg && "Register"}

            </Button>
        );
    }

    return (
        <>
            {(!appUserPersonOrganization) &&
                <div className={styles.container}>
                    {renderErrorMessage()}
                    <Field
                        label={"Your organization name"}
                        required
                        validationState={"none"}
                        validationMessage={""}>
                        <Input id={"org-onboarding-name-input"}
                               type="text"
                               maxLength={120}
                               value={organizationName}
                               onChange={onOrganizationNameChange}/>
                    </Field>

                    <Field
                        label={"Registration Number"}
                        required
                        validationState={"none"}
                        validationMessage={""}>
                        <Input id={"org-onboarding-registration-number-input"}
                               type="text"
                               maxLength={30}
                               value={registrationNumber}
                               onChange={onRegistrationNumberChange}/>
                    </Field>

                    <Field
                        label={"Phone Number"}
                        validationState={"none"}
                        validationMessage={""}>
                        <Input id={"org-onboarding-phone-input"}
                               type="text"
                               maxLength={30}
                               value={organizationPhone}
                               onChange={onOrganizationPhoneChange}/>
                    </Field>

                    <div>
                        <Field
                            label={"Email"}
                            validationState={"none"}
                            validationMessage={""}>
                            <Input id={"org-onboarding-email-input"}
                                   type="text"
                                   maxLength={120}
                                   value={organizationEmail}
                                   onChange={onOrganizationEmailChange}/>
                        </Field>
                        <Checkbox id={"org-onboarding-use-account-email-checkbox"}
                                  label={"Use my account sign in email"}
                                  onChange={onUseAppUserEmailCheck}/>
                    </div>
                    {!isOnDialog && renderRegisterButton()}
                    {isOnDialog &&
                        <div className={styles.dialogActions}>
                            {!orgRegistered && renderRegisterButton()}
                            <Button id={"org-onboarding-dialog-cancel-btn"}
                                    appearance={"secondary"}
                                    shape="circular"
                                    onClick={onCancel}>
                                {orgRegistered && "Close"}
                                {!orgRegistered && "Cancel"}
                            </Button>
                        </div>
                    }
                </div>
            }

            {appUserPersonOrganization &&
                <div className={styles.container}>
                    {isOnDialog &&
                        <div className={styles.dialogActions}>
                            <Button id={"org-onboarding-dialog-close-btn"}
                                    appearance={"secondary"}
                                    shape="circular"
                                    onClick={onCancel}>
                                {orgRegistered && "Close"}
                                {!orgRegistered && "Cancel"}
                            </Button>
                        </div>
                    }
                </div>
            }
        </>
    );
};

export default OrganizationOnboardingForm;