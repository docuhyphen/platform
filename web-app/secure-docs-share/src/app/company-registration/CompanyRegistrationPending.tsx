import React from 'react';
import './CompanyRegistrationPending.css';
import {useNavigate} from "react-router-dom";
import {Button} from "@fluentui/react-components";


const CompanyRegistrationPending: React.FC = () =>
{

    const navigate = useNavigate()

    return (
        <div>
            <h1>Company Registration Pending</h1>
            <p>
                Thank you for registering your company. Your company registration is pending. You will receive an email
                once your registration is approved.
            </p>
            <p>
                While you wait, you can continue using the platform as an individual.
                <Button onClick={() => navigate("/landing")}>Home </Button>
            </p>
        </div>
    );
};

export default CompanyRegistrationPending;
