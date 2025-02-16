import React, {useEffect} from 'react';
import {useNavigate} from "react-router-dom";
import {Button} from "@fluentui/react-components";
import {useAuth} from "../../context/AuthContext.tsx";
import {useCompanyRegistrationPendingStyles} from './CompanyRegistrationPendingStyles';

const CompanyRegistrationPending: React.FC = () =>
{
    const navigate = useNavigate();
    const {appUserPersonCompany} = useAuth();
    const styles = useCompanyRegistrationPendingStyles();

    useEffect(() =>
    {
        if (appUserPersonCompany)
        {
            if (appUserPersonCompany.registrationComplete)
            {
                navigate('/landing');
            }
        }
    }, [appUserPersonCompany]);

    return (
        <div className={styles.container}>
            <h1 className={styles.heading}>{appUserPersonCompany?.name} registration pending</h1>
            <p className={styles.paragraph}>
                Thank you for registering your company. Your company registration is pending. You will receive an email
                once your registration is approved.
            </p>
            <p className={styles.paragraph}>
                <Button className={styles.button} onClick={() => navigate("/sharing-sessions")}>Home</Button>
            </p>
        </div>
    );
};

export default CompanyRegistrationPending;