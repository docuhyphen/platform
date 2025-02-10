import React, {useEffect} from 'react';
import './CompanyRegistrationPending.css';
import {useNavigate} from "react-router-dom";
import {Button} from "@fluentui/react-components";
import {useAuth} from "../../context/AuthContext.tsx";


const CompanyRegistrationPending: React.FC = () =>
{
    const navigate = useNavigate()
    const {appUserPersonCompany} = useAuth();

    useEffect(() =>
    {
        if (appUserPersonCompany)
        {
            if (appUserPersonCompany.registrationComplete)
            {
                navigate('/landing')
            }
        }
    }, [appUserPersonCompany]);

    return (
        <div>
            <h1>{appUserPersonCompany?.name} registration pending</h1>
            <p>
                Thank you for registering your company. Your company registration is pending. You will receive an email
                once your registration is approved.
            </p>
            <p>
                <Button onClick={() => navigate("/sharing-sessions")}>Home </Button>
            </p>
        </div>
    );
};

export default CompanyRegistrationPending;
