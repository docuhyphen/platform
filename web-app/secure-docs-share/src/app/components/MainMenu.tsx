import React from 'react';
import { useNavigate } from 'react-router-dom';
import {Button, Link, Persona} from "@fluentui/react-components";
import { useAuth } from '../../context/AuthContext';
import SignOutButton from '../components/SignOutButton';

const MainMenu: React.FC = () => {
    const { appUser, appUserPersonCompany } = useAuth();
    const navigate = useNavigate();

    return (
        <div>
            <Link onClick={ () => navigate("/profile")}>Profile</Link>
            <Link onClick={ () => navigate("/settings")}>Settings</Link>
            {!appUserPersonCompany &&
                <Button onClick={() => navigate('/onboarding/company-registration')}>
                    Register Company
                </Button>
            }

            {(appUserPersonCompany && !appUserPersonCompany.registrationComplete) &&
                <p>
                    {appUserPersonCompany?.name} registration pending
                </p>
            }
            <Button appearance="primary" shape="circular">Share New Document</Button>
            <SignOutButton />
            <Persona
                name={`${appUser?.person?.firstName} ${appUser?.person?.lastName}`}
                secondaryText={appUser?.email}
                avatar={{
                    image: {
                        src: "https://res-1.cdn.office.net/files/fabric-cdn-prod_20230815.002/office-ui-fabric-react-assets/persona-male.png",
                    },
                }} />
        </div>
    );
};

export default MainMenu;