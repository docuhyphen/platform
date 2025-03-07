import React, {ChangeEvent, useState} from 'react';
import {Button, Checkbox, Field, Input, InputOnChangeData} from "@fluentui/react-components";
import {registerCompany} from "../../../services/userApi.ts";
import useToken from "../../../context/useToken.tsx";
import {Company} from "../../models/models.tsx";
import {useNavigate} from "react-router-dom";
import {useAuth} from "../../../context/AuthContext.tsx";
import {useOrganizationOnboardingForm} from './OrganizationOnboardingFormStyles.tsx';

const CompanyOnboardingForm: React.FC = () =>
{
    const [companyName, setCompanyName] = useState('');
    const [companyEmail, setCompanyEmail] = useState('');
    const [registrationNumber, setRegistrationNumber] = useState('');
    const [companyPhone, setCompanyPhone] = useState('');
    const [isRegistering, setIsRegistering] = useState(false);
    const token = useToken();
    const navigate = useNavigate();
    const {setAppUserPersonCompany, appUserPersonCompany, appUser} = useAuth();
    const styles = useOrganizationOnboardingForm();

    const onRegisterCompany = async () =>
    {
        setIsRegistering(true);
        try
        {
            const company = {name: companyName, registrationNumber};
            const registeredCompany: Company = await registerCompany(company, token);

            setAppUserPersonCompany(registeredCompany);
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

    const onCompanyNameChange = (_e: ChangeEvent<HTMLInputElement>, newValue: InputOnChangeData) =>
    {
        setCompanyName(newValue.value || '');
    }


    const onRegistrationNumberChange = (_e: ChangeEvent<HTMLInputElement>, newValue: InputOnChangeData) =>
    {
        setRegistrationNumber(newValue.value || '');
    }

    const onCompanyEmailChange = (_e: ChangeEvent<HTMLInputElement>, newValue: InputOnChangeData) =>
    {
        setCompanyEmail(newValue.value || '');
    }

    const onCompanyPhoneChange = (_e: ChangeEvent<HTMLInputElement>, newValue: InputOnChangeData) =>
    {
        setCompanyPhone(newValue.value || '');
    }

    const onSkipRegistration = () =>
    {
        navigate('/sharing-sessions');
    }

    const onUseAppUserEmailCheck = (ev: React.FormEvent<HTMLInputElement>, data: CheckboxOnChangeData) =>
    {
        if (data.checked)
        {
            setCompanyEmail(appUser.email);
        }
        else
        {
            setCompanyEmail('');
        }
    }

    return (
        <>
            {(!appUserPersonCompany) &&
                <div className={styles.container}>
                    <Field
                        label={"Organization Name"}
                        validationState={"none"}
                        validationMessage={""}>
                        <Input type="text" value={companyName} onChange={onCompanyNameChange}/>
                    </Field>

                    <Field
                        label={"Registration Number"}
                        validationState={"none"}
                        validationMessage={""}>
                        <Input type="text" value={registrationNumber} onChange={onRegistrationNumberChange}/>
                    </Field>


                    <Field
                        label={"Phone Number"}
                        validationState={"none"}
                        validationMessage={""}>
                        <Input type="text" value={registrationNumber} onChange={onRegistrationNumberChange}/>
                    </Field>

                    <div>

                        <Field
                            label={"Email"}
                            validationState={"none"}
                            validationMessage={""}>
                            <Input type="text"
                                   value={companyEmail}
                                   onChange={onCompanyEmailChange}/>
                        </Field>
                        <Checkbox label={"Use my email"}
                                  onChange={onUseAppUserEmailCheck}/>
                    </div>

                    <Button appearance={"transparent"}
                            onClick={onSkipRegistration}> Skip for later </Button>
                    <Button appearance={"primary"}
                            shape={"circular"}
                            onClick={onRegisterCompany}>
                        Register
                    </Button>
                </div>
            }
        </>
    );
};

export default CompanyOnboardingForm;