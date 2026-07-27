import {Button, mergeClasses} from "@fluentui/react-components";
import {Link, useNavigate} from "react-router-dom";
import AppLogo from "../../app-logo/AppLogo.tsx";
import {SIGN_IN_URL, SIGN_UP_URL} from "../shared.ts";
import {DesktopNavigationLinks} from "./DesktopNavigationLinks.tsx";
import {useLandingHeaderStyles} from "./LandingHeaderStyles.tsx";
import {MobileNavigationMenu} from "./MobileNavigationMenu.tsx";

interface LandingHeaderProps
{
    fixed?: boolean;
}

export function LandingHeader({fixed = false}: LandingHeaderProps)
{
    const styles = useLandingHeaderStyles();
    const navigate = useNavigate();

    return (
        <header
            id="landing-header"
            className={mergeClasses(styles.wrapper, fixed && styles.fixedWrapper)}
        >
            <nav
                id="landing-primary-navigation"
                className={styles.nav}
                aria-label="Primary"
            >
                <div
                    id="landing-navigation-left"
                    className={styles.leftGroup}
                >
                    <Link
                        id="landing-home-link"
                        to="/"
                        aria-label="DocuHyphen home"
                        className={styles.logo}
                    >
                        <AppLogo/>
                    </Link>
                    <DesktopNavigationLinks onNavigate={navigate}/>
                </div>

                <div
                    id="landing-navigation-actions"
                    className={styles.rightActions}
                >
                    <Button
                        id="landing-start-free"
                        appearance="primary"
                        size={"small"}
                        as="a"
                        className={styles.tryFreeButton}
                        target="_blank"
                        rel="noopener noreferrer"
                        shape="circular"
                        href={SIGN_UP_URL}
                    >
                        Start Free
                    </Button>
                    <Button
                        id="landing-sign-in"
                        appearance="outline"
                        as="a"
                        size={"small"}
                        className={styles.signInButton}
                        target="_blank"
                        rel="noopener noreferrer"
                        shape="circular"
                        href={SIGN_IN_URL}
                    >
                        Sign in
                    </Button>
                    <MobileNavigationMenu onNavigate={navigate}/>
                </div>
            </nav>
        </header>
    );
}
