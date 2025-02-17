import React, {ChangeEvent, useEffect, useState} from 'react';
import {Button, Field, Input, InputOnChangeData} from "@fluentui/react-components";
import {registerCompany} from "../../services/userApi.ts";
import useToken from "../../context/useToken.tsx";
import {Company} from "../models/models.tsx";
import {useNavigate} from "react-router-dom";
import {useAuth} from "../../context/AuthContext.tsx";
import {useCompanyRegistrationStyles} from './CompanyRegistrationStyles';

const CompanyRegistration: React.FC = () =>
{
    const [companyName, setCompanyName] = useState('');
    const [registrationNumber, setRegistrationNumber] = useState('');
    const [isRegistering, setIsRegistering] = useState(false);
    const token = useToken();
    const navigate = useNavigate();
    const {setAppUserPersonCompany, appUserPersonCompany} = useAuth();
    const styles = useCompanyRegistrationStyles();

    useEffect(() =>
    {
        if (!isRegistering && appUserPersonCompany)
        {
            if (appUserPersonCompany.registrationComplete)
            {
                navigate('/landing');
            }
            else
            {
                navigate('/onboarding/company-registration-pending');
            }
        }
    }, [appUserPersonCompany, isRegistering, navigate]);

    const onRegisterCompany = async () =>
    {
        setIsRegistering(true);
        try
        {
            const company = {name: companyName, registrationNumber};
            const registeredCompany: Company = await registerCompany(company, token);

            setAppUserPersonCompany(registeredCompany);
            navigate('/onboarding/company-registration-pending');
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

    function onCompanyNameChange(_e: ChangeEvent<HTMLInputElement>, newValue: InputOnChangeData)
    {
        setCompanyName(newValue.value || '');
    }

    function onRegistrationNumberChange(_e: ChangeEvent<HTMLInputElement>, newValue: InputOnChangeData)
    {
        setRegistrationNumber(newValue.value || '');
    }

    return (
        <>
            {(!appUserPersonCompany) &&
                <div className={styles.container}>
                    <h1 className={styles.heading}>Company Registration</h1>

                    <Field
                        label={"Company Name"}
                        validationState={"none"}
                        validationMessage={""}
                        className={styles.field}
                    >
                        <Input type="text" value={companyName} onChange={onCompanyNameChange}/>
                    </Field>

                    <Field
                        label={"Company Registration Number"}
                        validationState={"none"}
                        validationMessage={""}
                        className={styles.field}
                    >
                        <Input type="text" value={registrationNumber} onChange={onRegistrationNumberChange}/>
                    </Field>

                    <Button className={styles.button} onClick={onRegisterCompany}> Register </Button>
                </div>
            }
        </>
    );
};

export default CompanyRegistration;