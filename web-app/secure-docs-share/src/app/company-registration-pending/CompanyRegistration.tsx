import React, {ChangeEvent, useEffect, useState} from 'react';
import './CompanyRegistration.css';
import {Button, Field, Input, InputOnChangeData} from "@fluentui/react-components";
import {registerCompany} from "../../services/api.ts";
import useToken from "../../context/useToken.tsx";
import {Company} from "../models/models.tsx";
import {useNavigate} from "react-router-dom";
import {useAuth} from "../../context/AuthContext.tsx";


const CompanyRegistration: React.FC = () =>
{
    const [companyName, setCompanyName] = useState('')
    const [registrationNumber, setRegistrationNumber] = useState('')
    const token = useToken()
    const navigate = useNavigate()

    const {setAppUserPersonCompany, appUserPersonCompany} = useAuth();

    useEffect(() =>
    {

        if (appUserPersonCompany && appUserPersonCompany.registrationComplete)
        {
            navigate('/landing')
        }
        else
        {
            navigate('/onboarding/company-registration-pending')
        }
    }, [])

    const onRegisterCompany = async () =>
    {
        try
        {
            const company = {name: companyName, registrationNumber};
            const registeredCompany: Company = await registerCompany(company, token); // Assume this API call returns the updated user

            setAppUserPersonCompany(registeredCompany);
            navigate('/onboarding/company-registration-pending');
        }
        catch (error)
        {
            console.error('Registration failed', error);
        }
    };

    function onCompanyNameChange(_e: ChangeEvent<HTMLInputElement>, newValue: InputOnChangeData)
    {
        setCompanyName(newValue.value || '')
    }

    function onRegistrationNumberChange(_e: ChangeEvent<HTMLInputElement>, newValue: InputOnChangeData)
    {
        setRegistrationNumber(newValue.value || '')
    }

    return (
        <div>
            <h1>Company Registration</h1>

            <Field
                label={"Company Name"}
                validationState={"none"}
                validationMessage={""}>

                <Input type="text"
                       value={companyName}
                       onChange={onCompanyNameChange}/>
            </Field>

            <Field
                label={"Company Registration Number"}
                validationState={"none"}
                validationMessage={""}>

                <Input type="text"
                       value={registrationNumber}
                       onChange={onRegistrationNumberChange}/>
            </Field>

            <Button onClick={onRegisterCompany}> Register </Button>
        </div>
    );
};

export default CompanyRegistration;