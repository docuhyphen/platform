import React, {ChangeEvent, useState} from 'react';
import {Button, Checkbox, Field, Input, InputOnChangeData, Spinner} from "@fluentui/react-components";
import {registerOrganization} from "../../../services/appUserApi.ts";
import useToken from "../../../context/useToken.tsx";
import {OrganizationBasicDto} from "../../models/models.tsx";
import {useNavigate} from "react-router-dom";
import {useAuth} from "../../../context/AuthContext.tsx";
import {useOrganizationOnboardingForm} from './OrganizationOnboardingFormStyles.tsx';
import {useGlobalStyles} from "../../../GlobalStyles.tsx";


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
    const token = useToken();
    const navigate = useNavigate();
    const {setAppUserPersonOrganization, appUserPersonOrganization, appUser} = useAuth();
    const styles = useOrganizationOnboardingForm();
    const globalStyles = useGlobalStyles();

    const onRegisterOrganization = async () =>
    {
        setRegisteringOrg(true);

        try
        {
            const organization = {
                name: organizationName,
                registrationNumber,
                email: organizationEmail,
                phoneNumber: organizationPhone,
            };
            const registeredOrganization: OrganizationBasicDto = await registerOrganization(organization, token);

            setAppUserPersonOrganization(registeredOrganization);

            if (onOrganizationRegistered)
            {
                onOrganizationRegistered(registeredOrganization);
            }
        }
        catch (error)
        {
            console.error('Registration failed', error);
        }
        finally
        {
            setRegisteringOrg(false);
        }
    };

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
            <Button appearance={"primary"}
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
                    <Field
                        label={"Your organization name"}
                        validationState={"none"}
                        validationMessage={""}>
                        <Input type="text"
                               maxLength={120}
                               value={organizationName}
                               onChange={onOrganizationNameChange}/>
                    </Field>

                    <Field
                        label={"Registration Number"}
                        validationState={"none"}
                        validationMessage={""}>
                        <Input type="text"
                               maxLength={30}
                               value={registrationNumber}
                               onChange={onRegistrationNumberChange}/>
                    </Field>

                    <Field
                        label={"Phone Number"}
                        validationState={"none"}
                        validationMessage={""}>
                        <Input type="text"
                               maxLength={30}
                               value={organizationPhone}
                               onChange={onOrganizationPhoneChange}/>
                    </Field>

                    <div>
                        <Field
                            label={"Email"}
                            validationState={"none"}
                            validationMessage={""}>
                            <Input type="text"
                                   maxLength={120}
                                   value={organizationEmail}
                                   onChange={onOrganizationEmailChange}/>
                        </Field>
                        <Checkbox label={"Use my account sign in email"}
                                  onChange={onUseAppUserEmailCheck}/>
                    </div>
                    {!isOnDialog && renderRegisterButton()}
                    {isOnDialog &&
                        <div className={styles.dialogActions}>
                            {!orgRegistered && renderRegisterButton()}
                            <Button appearance={"secondary"}
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
                            <Button appearance={"secondary"}
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