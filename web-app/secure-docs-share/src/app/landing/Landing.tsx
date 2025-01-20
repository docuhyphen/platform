import React from 'react';
import SignOutButton from "../components/SignOutButton.tsx";
import {useAuth} from '../../context/AuthContext';
import {useNavigate} from 'react-router-dom';
import {Avatar, Button, Persona} from "@fluentui/react-components";

const Landing: React.FC = () =>
{
    const {appUser, appUserPersonCompany} = useAuth();
    const navigate = useNavigate();

    const handleRegisterCompany = () =>
    {
        navigate('/onboarding/company-registration');
    };

    return (
        <div>
            <div>
                {!appUserPersonCompany &&
                    <Button onClick={handleRegisterCompany}> Register Company</Button>
                }
                <SignOutButton/>
                <Persona
                    name={`${appUser?.person?.firstName} ${appUser?.person?.lastName}`}
                    secondaryText={appUser?.email}
                    avatar={{
                        image: {
                            src: "https://res-1.cdn.office.net/files/fabric-cdn-prod_20230815.002/office-ui-fabric-react-assets/persona-male.png",
                        },
                    }}/>
            </div>
            <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center'}}>
                <h1>Welcome to the Landing Page</h1>
            </div>
            <p>You have successfully signed in!</p>
        </div>
    );
};

export default Landing;