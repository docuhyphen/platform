import React from 'react';
import SignOutButton from "../components/SignOutButton.tsx";
import IndividualRegistration from '../individual-registration/IndividualRegistration';

const Onboarding: React.FC = () => {
    return (
        <div>
            <h1>Welcome,</h1>
            <p>Please complete your registration</p>
            <IndividualRegistration />
        </div>
    );
};


export default Onboarding;