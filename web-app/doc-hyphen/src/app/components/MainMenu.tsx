import React, {useState} from 'react';
import {useNavigate} from 'react-router-dom';
import {
    Button,
    Dialog,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    Menu,
    MenuButton,
    MenuItem,
    MenuList,
    MenuPopover,
    MenuTrigger,
    Persona,
    Spinner
} from "@fluentui/react-components";
import {useAuth} from '../../context/AuthContext';
import SignOutButton from '../components/SignOutButton';
import SharingSessionInitiation from "../sharing-session-initiation/SharingSessionInitiation.tsx";
import {AlertRegular, ArrowExitRegular, ChannelShareRegular, InfoRegular, SettingsRegular} from "@fluentui/react-icons";
import AppLogo from "./app-logo/AppLogo.tsx";
import {useGlobalStyles} from "../../GlobalStyles.tsx";

const MainMenu: React.FC = () => {
    const { appUser, appUserPersonCompany } = useAuth();
    const navigate = useNavigate();
    const [isDialogOpen, setIsDialogOpen] = useState(false);

    function onRequestDocuments()
    {
        navigate('/sharing-session-initiation?request=true');
    }

    function onSendDocuments()
    {
        navigate('/sharing-session-initiation?request=false');
    }

    const onSignOut = () =>
    {
        setIsDialogOpen(true);
    };

    const styles = useGlobalStyles();

    return (
        <section className={styles.mainAppHeader}>
            <AppLogo/>
            <section className={styles.mainAppHeaderMidSection}>
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
                                <SignOutButton onSignOut={onSignOut}/>
                            </MenuItem>
                        </MenuList>
                    </MenuPopover>
                </Menu>
            </section>

            <Dialog open={isDialogOpen}>
                <DialogSurface>
                    <DialogBody>
                        <DialogTitle></DialogTitle>
                        <DialogContent>
                            <Spinner label="Signing out..."/>
                        </DialogContent>
                    </DialogBody>
                </DialogSurface>
            </Dialog>
        </section>
    );
};

export default MainMenu;