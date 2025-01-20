import React from 'react';
import SignOutButton from "../components/SignOutButton.tsx";

const Landing: React.FC = () =>
{
    return (
        <div>
            <h1>Welcome to the Landing Page</h1>
            <p>You have successfully signed in!</p>
            <SignOutButton/>
        </div>
    );
};

export default Landing;