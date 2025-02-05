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
import {
    AlertRegular,
    ArrowExitRegular,
    ChannelShareRegular, InfoRegular,
    PersonSettingsRegular,
    SettingsRegular
} from "@fluentui/react-icons";
import SharingSessionInitiation from "../sharing-session-initiation/SharingSessionInitiation.tsx";

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
            <span id="app-logo">
                <span id="logo-doc">DOC</span> <span>-</span><br/>HYPHEN
            </span>
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
                <SharingSessionInitiation/>
            </section>

            <section>
                <Button icon={<ChannelShareRegular/>}
                        onClick={() => navigate('/landing')}
                        appearance={"subtle"}>
                </Button>
                <Button icon={<AlertRegular/>}
                        appearance={"subtle"}>
                </Button>
                <Button icon={<InfoRegular/>}
                        onClick={() => navigate('/landing')}
                        appearance={"subtle"}>
                </Button>
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
        </section>
    );
};

export default MainMenu;