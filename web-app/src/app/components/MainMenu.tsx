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
    Spinner, Text
} from "@fluentui/react-components";
import {useAuth} from '../../context/AuthContext';
import SignOutClickSurface from './SignOutClickSurface.tsx';
import ExchangeInitiation from "../exchange-initiation/ExchangeInitiation.tsx";
import AppLogo from "./app-logo/AppLogo.tsx";
import {useGlobalStyles} from "../../GlobalStyles.tsx";
import {InfoIcon, SettingsIcon, ExchangeIcon, SignOutButtonIcon} from "./IconBundles.tsx";
import NotificationsPanel from './main-menu/notifications-panel/NotificationsPanel';
import {useMainMenuStyles} from "./MainMenuStyles.tsx";

const MAX_DISPLAY_EMAIL_LENGTH = 36;
const LAST_EXCHANGES_QUERY_STORAGE_KEY = 'exchanges.lastRoute.query';

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

const MainMenu: React.FC<{ onToggleHelpSidebar: () => void }> = ({onToggleHelpSidebar}) =>
{
    const {appUser, appUserPersonOrganization} = useAuth();
    const navigate = useNavigate();
    const [isSignOutDialogOpen, setIsSignOutDialogOpen] = useState(false);

    const onSignOut = () =>
    {
        setIsSignOutDialogOpen(true);
    };

    const styles = useGlobalStyles();
    const menuStyles = useMainMenuStyles();

    return (
        <section className={styles.mainAppHeader}>
            <span className={styles.mainHeaderAppLogo}>
                <AppLogo/>

                {appUserPersonOrganization &&
                    appUserPersonOrganization.isActive &&
                    appUserPersonOrganization.verificationComplete &&
                    <Text className={styles.mainHeaderOrgTitle}>
                        {appUserPersonOrganization.name}
                    </Text>
                }
            </span>

            {/* Tour anchor: Start Exchanging */}
            <div
                id="tour-start-exchanging"
                className={menuStyles.tourAnchor}>
                <ExchangeInitiation/>
            </div>

            {/* Tour anchor: Exchanges */}
            <div
                id="tour-sessions-btn"
                className={menuStyles.tourAnchor}>
                <Button
                    id={"exchanges-nav-btn"}
                    icon={<ExchangeIcon/>}
                    shape={"circular"}
                    onClick={() =>
                    {
                        if (window.location.pathname !== '/exchanges')
                        {
                            const savedQuery = window.localStorage.getItem(LAST_EXCHANGES_QUERY_STORAGE_KEY) || '';
                            navigate(`/exchanges${savedQuery}`);
                        }
                    }}
                    appearance={"subtle"}>
                </Button>
            </div>

            {/* Tour anchor: Notifications */}
            <div
                id="tour-notifications"
                className={menuStyles.tourAnchor}>
                <NotificationsPanel/>
            </div>


            {/*<Button icon={<InfoIcon/>}*/}
            {/*        onClick={() => navigate('/')}*/}
            {/*        appearance={"subtle"}>*/}
            {/*</Button>*/}

            {/* Tour anchor: Account menu */}
            <div
                id="tour-account-btn"
                className={menuStyles.tourAnchor}>
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
                        <Button
                                id={"account-menu-btn"}
                                appearance="subtle"
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
                            <MenuItem onClick={onToggleHelpSidebar}
                                      icon={<InfoIcon/>}>
                                Help
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
        </section>
    );
};

export default MainMenu;
