import React from 'react';
import {useNavigate} from 'react-router-dom';
import {
    Button,
    Link,
    Menu,
    MenuButton,
    MenuItem,
    MenuList,
    MenuPopover,
    MenuTrigger,
    Persona
} from "@fluentui/react-components";
import {useAuth} from '../../context/AuthContext';
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

            <Menu>
                <MenuTrigger disableButtonEnhancement>
                    <MenuButton shape="circular" appearance="primary" >Start Share Session</MenuButton>
                </MenuTrigger>

                <MenuPopover>
                    <MenuList>
                        <MenuItem>Request Documents</MenuItem>
                        <MenuItem disabled={true}>Send Documents</MenuItem> {/* Feature can be optional*/}
                    </MenuList>
                </MenuPopover>
            </Menu>

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