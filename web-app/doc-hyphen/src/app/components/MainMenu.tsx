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
import AppLogo from "./app-logo/AppLogo.tsx";
import {useGlobalStyles} from "../../GlobalStyles.tsx";
import {NotificationsIcon, SettingsIcon, SharingSessionIcon, SignOutButtonIcon} from "./IconBundles.tsx";

const MainMenu: React.FC = () =>
{
    const {appUser} = useAuth();
    const navigate = useNavigate();
    const [isSignOutDialogOpen, setIsSignOutDialogOpen] = useState(false);

    const onSignOut = () =>
    {
        setIsSignOutDialogOpen(true);
    };

    const styles = useGlobalStyles();

    return (
        <section className={styles.mainAppHeader}>
            <span className={styles.mainHeaderAppLogo}>
                <AppLogo/>
            </span>

            <SharingSessionInitiation/>

            <Button icon={<SharingSessionIcon/>}
                    onClick={() => navigate('/')}
                    appearance={"subtle"}>
            </Button>
            <Button icon={<NotificationsIcon/>}
                    appearance={"subtle"}>
            </Button>
            {/*<Button icon={<InfoIcon/>}*/}
            {/*        onClick={() => navigate('/')}*/}
            {/*        appearance={"subtle"}>*/}
            {/*</Button>*/}
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
                                  icon={<SettingsIcon/>}>
                            Settings
                        </MenuItem>
                        <MenuItem icon={<SignOutButtonIcon/>}>
                            <SignOutButton onSignOut={onSignOut}/>
                        </MenuItem>
                    </MenuList>
                </MenuPopover>
            </Menu>

            <Dialog open={isSignOutDialogOpen}>
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