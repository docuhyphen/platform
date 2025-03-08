import React, {ChangeEvent, useState} from 'react';
import {Button, Checkbox, Field, Input, InputOnChangeData} from "@fluentui/react-components";
import {registerOrganization} from "../../../services/userApi.ts";
import useToken from "../../../context/useToken.tsx";
import {Organization} from "../../models/models.tsx";
import {useNavigate} from "react-router-dom";
import {useAuth} from "../../../context/AuthContext.tsx";
import {useOrganizationOnboardingForm} from './OrganizationOnboardingFormStyles.tsx';

const OrganizationOnboardingForm: React.FC = () =>
{
    const [organizationName, setOrganizationName] = useState('');
    const [organizationEmail, setOrganizationEmail] = useState('');
    const [registrationNumber, setRegistrationNumber] = useState('');
    const [organizationPhone, setOrganizationPhone] = useState('');
    const [isRegistering, setIsRegistering] = useState(false);
    const token = useToken();
    const navigate = useNavigate();
    const {setAppUserPersonOrganization, appUserPersonOrganization, appUser} = useAuth();
    const styles = useOrganizationOnboardingForm();

    const onRegisterOrganization = async () =>
    {
        setIsRegistering(true);
        try
        {
            const organization = {name: organizationName, registrationNumber};
            const registeredOrganization: Organization = await registerOrganization(organization, token);

            setAppUserPersonOrganization(registeredOrganization);
        }
        catch (error)
        {
            console.error('Registration failed', error);
        }
        finally
        {
            setIsRegistering(false);
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

    return (
        <>
            {(!appUserPersonOrganization) &&
                <div className={styles.container}>
                    <Field
                        label={"Organization Name"}
                        validationState={"none"}
                        validationMessage={""}>
                        <Input type="text"
                               value={organizationName}
                               onChange={onOrganizationNameChange}/>
                    </Field>

                    <Field
                        label={"Registration Number"}
                        validationState={"none"}
                        validationMessage={""}>
                        <Input type="text"
                               value={registrationNumber}
                               onChange={onRegistrationNumberChange}/>
                    </Field>

                    <Field
                        label={"Phone Number"}
                        validationState={"none"}
                        validationMessage={""}>
                        <Input type="text"
                               value={registrationNumber}
                               onChange={onOrganizationPhoneChange}/>
                    </Field>

                    <div>
                        <Field
                            label={"Email"}
                            validationState={"none"}
                            validationMessage={""}>
                            <Input type="text"
                                   value={organizationEmail}
                                   onChange={onOrganizationEmailChange}/>
                        </Field>
                        <Checkbox label={"Use my email"}
                                  onChange={onUseAppUserEmailCheck}/>
                    </div>

                    <Button appearance={"primary"}
                            shape={"circular"}
                            onClick={onRegisterOrganization}>
                        Register
                    </Button>
                </div>
            }
        </>
    );
};

export default OrganizationOnboardingForm;