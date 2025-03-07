import React, {ChangeEvent, useEffect, useState} from 'react';
import {Button, Checkbox, Field, Input, InputOnChangeData} from "@fluentui/react-components";
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
        console.log("CompanyRegistration useEffect appUserPersonCompany", appUserPersonCompany);
        // if (!isRegistering && appUserPersonCompany)
        // {
        //     if (appUserPersonCompany.registrationComplete)
        //     {
        //         navigate('/sharing-sessions');
        //     }
        //     else
        //     {
        //         navigate('/onboarding/company-registration-pending');
        //     }
        // }
    }, [appUserPersonCompany]);
    // }, [appUserPersonCompany, isRegistering, navigate]);

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

    const onCompanyNameChange = (_e: ChangeEvent<HTMLInputElement>, newValue: InputOnChangeData) =>
    {
        setCompanyName(newValue.value || '');
    }

    const onRegistrationNumberChange = (_e: ChangeEvent<HTMLInputElement>, newValue: InputOnChangeData) =>
    {
        setRegistrationNumber(newValue.value || '');
    }

    const onSkipRegistration = () =>
    {
        navigate('/sharing-sessions');
    }

    return (
        <>
            {(!appUserPersonCompany) &&
                <div className={styles.container}>
                    <h1 className={styles.heading}>Organization</h1>

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

                    <Field
                        label={"Email"}
                        validationState={"none"}
                        validationMessage={""}
                        className={styles.field}
                    >
                        <Input type="text" value={registrationNumber} onChange={onRegistrationNumberChange}/>
                    </Field>
                    <Field label={"Use my email"}>
                        <Checkbox/>
                    </Field>

                    <Field
                        label={"Phone Number"}
                        validationState={"none"}
                        validationMessage={""}
                        className={styles.field}>
                        <Input type="text" value={registrationNumber} onChange={onRegistrationNumberChange}/>
                    </Field>

                    <Button className={styles.button}
                            appearance={"transparent"}
                            onClick={onSkipRegistration}> Skip for later </Button>
                    <Button className={styles.button}
                            appearance={"primary"}
                            shape={"circular"}
                            onClick={onRegisterCompany}>
                        Register
                    </Button>
                </div>
            }
        </>
    );
};

export default CompanyRegistration;