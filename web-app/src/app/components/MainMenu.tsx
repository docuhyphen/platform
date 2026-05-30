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
import SignOutClickSurface from './SignOutClickSurface.tsx';
import SharingSessionInitiation from "../sharing-session-initiation/SharingSessionInitiation.tsx";
import AppLogo from "./app-logo/AppLogo.tsx";
import {useGlobalStyles} from "../../GlobalStyles.tsx";
import {SettingsIcon, SharingSessionIcon, SignOutButtonIcon} from "./IconBundles.tsx";
import NotificationList from './main-menu/notification/NotificationList';

const MAX_DISPLAY_EMAIL_LENGTH = 36;
const LAST_SHARING_SESSIONS_QUERY_STORAGE_KEY = 'sharingSessions.lastRoute.query';

function formatEmailForDisplay(email?: string, maxLength: number = MAX_DISPLAY_EMAIL_LENGTH): string | undefined
{
    if (!email)
    {
        return undefined;
    }

    if (email.length <= maxLength)
    {
        return email;
    }

    const atIndex = email.indexOf('@');
    if (atIndex === -1)
    {
        return `${email.slice(0, Math.max(0, maxLength - 3))}...`;
    }

    const localPart = email.slice(0, atIndex);
    const domainPart = email.slice(atIndex + 1);
    const allowedLocalLength = maxLength - domainPart.length - 4;

    if (allowedLocalLength < 6)
    {
        return `${email.slice(0, Math.max(0, maxLength - 3))}...`;
    }

    return `${localPart.slice(0, allowedLocalLength)}...@${domainPart}`;
}

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
                    onClick={() =>
                    {
                        if (window.location.pathname !== '/sharing-sessions')
                        {
                            const savedQuery = window.localStorage.getItem(LAST_SHARING_SESSIONS_QUERY_STORAGE_KEY) || '';
                            navigate(`/sharing-sessions${savedQuery}`);
                        }
                    }}
                    appearance={"subtle"}>
            </Button>

            <NotificationList/>

            {/*<Button icon={<InfoIcon/>}*/}
            {/*        onClick={() => navigate('/')}*/}
            {/*        appearance={"subtle"}>*/}
            {/*</Button>*/}
            <Menu>
                <MenuTrigger disableButtonEnhancement>
                    <MenuButton appearance="transparent"
                                title={appUser?.email}>
                        <Persona
                            name={`${appUser?.person?.firstName} ${appUser?.person?.lastName}`}
                            secondaryText={formatEmailForDisplay(appUser?.email)}/>
                    </MenuButton>
                </MenuTrigger>

                <MenuPopover>
                    <MenuList>
                        <MenuItem onClick={() => navigate("/settings")}
                                  icon={<SettingsIcon/>}>
                            Settings
                        </MenuItem>
                        <MenuItem icon={<SignOutButtonIcon/>}>
                            <SignOutClickSurface onSignOut={onSignOut}/>
                        </MenuItem>
                    </MenuList>
                </MenuPopover>
            </Menu>

            <Dialog open={isSignOutDialogOpen}>
                <DialogSurface>
                    <DialogBody>
                        <DialogTitle></DialogTitle>
                        <DialogContent>
                            <Spinner label="Signing out..."
                                     size={"small"}/>
                        </DialogContent>
                    </DialogBody>
                </DialogSurface>
            </Dialog>
        </section>
    );
};

export default MainMenu;