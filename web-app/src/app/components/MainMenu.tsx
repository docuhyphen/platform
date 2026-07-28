import React from "react";
import {Button, Text} from "@fluentui/react-components";
import {useNavigate} from "react-router-dom";
import {useAuth} from "../../context/AuthContext.tsx";
import {useGlobalStyles} from "../../GlobalStyles.tsx";
import ExchangeInitiation from "../exchange-initiation/ExchangeInitiation.tsx";
import AppLogo from "./app-logo/AppLogo.tsx";
import {ExchangeIcon} from "./IconBundles.tsx";
import AccountMenu from "./main-menu/account-menu/AccountMenu.tsx";
import NotificationsPanel from "./main-menu/notifications-panel/NotificationsPanel.tsx";
import PlatformNavigation from "./main-menu/platform-navigation/PlatformNavigation.tsx";
import {useMainMenuStyles} from "./MainMenuStyles.tsx";

const LAST_EXCHANGES_QUERY_STORAGE_KEY = "exchanges.lastRoute.query";

const MainMenu: React.FC<{onToggleHelpSidebar: () => void}> = ({onToggleHelpSidebar}) =>
{
    const {appUserPersonOrganization} = useAuth();
    const navigate = useNavigate();
    const styles = useGlobalStyles();
    const menuStyles = useMainMenuStyles();

    const navigateToExchanges = () =>
    {
        if (window.location.pathname !== "/exchanges")
        {
            const savedQuery = window.localStorage.getItem(LAST_EXCHANGES_QUERY_STORAGE_KEY) || "";
            navigate(`/exchanges${savedQuery}`);
        }
    };

    return (
        <section
            id={"main-menu"}
            className={styles.mainAppHeader}>
            <span
                id={"main-menu-brand"}
                className={styles.mainHeaderAppLogo}>
                <AppLogo/>
                {appUserPersonOrganization?.isActive &&
                    appUserPersonOrganization.verificationComplete && (
                        <Text
                            id={"main-menu-organization-name"}
                            className={styles.mainHeaderOrgTitle}>
                            {appUserPersonOrganization.name}
                        </Text>
                    )}
            </span>

            <div
                id={"tour-start-exchanging"}
                className={menuStyles.tourAnchor}>
                <ExchangeInitiation/>
            </div>
            <div
                id={"tour-sessions-btn"}
                className={menuStyles.tourAnchor}>
                <Button
                    id={"exchanges-nav-btn"}
                    icon={<ExchangeIcon/>}
                    shape={"circular"}
                    appearance={"subtle"}
                    aria-label={"Exchanges"}
                    title={"Exchanges"}
                    onClick={navigateToExchanges}/>
            </div>
            <div
                id={"tour-notifications"}
                className={menuStyles.tourAnchor}>
                <NotificationsPanel/>
            </div>
            <PlatformNavigation/>
            <AccountMenu onToggleHelpSidebar={onToggleHelpSidebar}/>
        </section>
    );
};

export default MainMenu;
