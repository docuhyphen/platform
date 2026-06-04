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
import PendingApprovals from './main-menu/pending-approvals/PendingApprovals';
import TourCoach from './tour/TourCoach';

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
    const {appUser, appUserPersonOrganization} = useAuth();
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

            {/* Tour anchor: Start Sharing */}
            <div id="tour-start-sharing" style={{display: 'inline-flex', alignItems: 'center'}}>
                <SharingSessionInitiation/>
            </div>

            {/* Tour anchor: Sharing Sessions */}
            <div id="tour-sessions-btn" style={{display: 'inline-flex', alignItems: 'center'}}>
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
            </div>

            {/* Tour anchor: Notifications */}
            <div id="tour-notifications" style={{display: 'inline-flex', alignItems: 'center'}}>
                <NotificationList/>
            </div>

            {appUserPersonOrganization?.isActive && <PendingApprovals/>}

            {/*<Button icon={<InfoIcon/>}*/}
            {/*        onClick={() => navigate('/')}*/}
            {/*        appearance={"subtle"}>*/}
            {/*</Button>*/}

            {/* Tour anchor: Account menu */}
            <div id="tour-account-btn" style={{display: 'inline-flex', alignItems: 'center'}}>
                <Menu>
                    <MenuTrigger disableButtonEnhancement>
                        {/*
                          Use a plain Button (not MenuButton) so no dropdown
                          chevron is rendered. The Persona inside shows the
                          avatar + name/email on desktop; on phones our
                          `mainHeaderPersona` style hides the text so the
                          button collapses to a square avatar that matches
                          the surrounding icon buttons.
                        */}
                        <Button appearance="subtle"
                                shape="circular"
                                className={styles.mainHeaderPersona}
                                aria-label={appUser?.email ? `Account menu for ${appUser.email}` : "Account menu"}
                                title={appUser?.email}>
                            <Persona
                                name={`${appUser?.person?.firstName} ${appUser?.person?.lastName}`}
                                secondaryText={formatEmailForDisplay(appUser?.email)}/>
                        </Button>
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
            </div>

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

            {/* First-time feature tour */}
            <TourCoach/>
        </section>
    );
};

export default MainMenu;