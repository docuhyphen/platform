import React from 'react';
import {useNavigate} from 'react-router-dom';
import {
    Button,
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
import {ArrowExitRegular, PersonSettingsRegular, SettingsRegular} from "@fluentui/react-icons";

const MainMenu: React.FC = () => {
    const { appUser, appUserPersonCompany } = useAuth();
    const navigate = useNavigate();

    function onRequestDocuments()
    {
        navigate('/sharing-session-initiation?request=true');
    }

    function onSendDocuments()
    {
        navigate('/sharing-session-initiation?request=false');
    }

    return (
        <section id="main-app-header">
            <span></span>
            <section id="main-app-header-mid-section">
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
                        <MenuButton shape="circular" appearance="primary">Start Sharing Session</MenuButton>
                    </MenuTrigger>

                    <MenuPopover>
                        <MenuList>
                            <MenuItem onClick={onRequestDocuments}>Request Documents</MenuItem>
                            <MenuItem onClick={onSendDocuments}>Send Documents</MenuItem> {/* Feature can be optional*/}
                        </MenuList>
                    </MenuPopover>
                </Menu>
            </section>

            <Menu>
                <MenuTrigger disableButtonEnhancement>
                    <MenuButton appearance="transparent">
                        <Persona
                            name={`${appUser?.person?.firstName} ${appUser?.person?.lastName}`}
                            secondaryText={appUser?.email}/>
                    </MenuButton>
                </MenuTrigger>

                <MenuPopover>
                    <MenuList>
                        <MenuItem onClick={() => navigate("/profile")}
                                  icon={<PersonSettingsRegular/>}>
                            Profile
                        </MenuItem>
                        <MenuItem onClick={() => navigate("/settings")}
                                  icon={<SettingsRegular/>}>
                            Settings
                        </MenuItem>
                        <MenuItem icon={<ArrowExitRegular/>}>
                            <SignOutButton/>
                        </MenuItem>
                    </MenuList>
                </MenuPopover>
            </Menu>

        </section>
    );
};

export default MainMenu;