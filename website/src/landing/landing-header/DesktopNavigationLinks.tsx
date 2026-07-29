import {Menu, MenuItem, MenuList, MenuPopover, MenuTrigger, Text, mergeClasses} from "@fluentui/react-components";
import {ChevronDown16Regular} from "@fluentui/react-icons";
import {Link} from "react-router-dom";
import {LANDING_INDUSTRIES, toNavigationId} from "./landingNavigation.ts";
import {useLandingHeaderStyles} from "./LandingHeaderStyles.tsx";

interface DesktopNavigationLinksProps
{
    activePath: string;
    onNavigate: (to: string) => void;
}

export function DesktopNavigationLinks({activePath, onNavigate}: DesktopNavigationLinksProps)
{
    const styles = useLandingHeaderStyles();
    const industriesActive = activePath.startsWith("/industries");
    const getNavLinkClassName = (to: string) =>
        mergeClasses(styles.navLink, activePath === to && styles.activeNavLink);

    return (
        <div
            id="landing-desktop-links"
            className={styles.desktopLinks}
        >
            <Menu>
                <MenuTrigger disableButtonEnhancement>
                    <button
                        id="landing-industries-menu-trigger"
                        className={mergeClasses(styles.navLink, industriesActive && styles.activeNavLink)}
                        type="button"
                        aria-current={industriesActive ? "page" : undefined}
                    >
                        Industries <ChevronDown16Regular/>
                    </button>
                </MenuTrigger>
                <MenuPopover>
                    <MenuList>
                        {LANDING_INDUSTRIES.map((industry) => (
                            <MenuItem
                                key={industry.to}
                                id={`landing-industry-${toNavigationId(industry.label)}`}
                                onClick={() => onNavigate(industry.to)}
                                className={activePath === industry.to ? styles.activeSubMenuItem : undefined}
                                aria-current={activePath === industry.to ? "page" : undefined}
                            >
                                {industry.label}
                            </MenuItem>
                        ))}
                    </MenuList>
                </MenuPopover>
            </Menu>

            <Link
                id="landing-pricing-link"
                to="/pricing"
                className={getNavLinkClassName("/pricing")}
                aria-current={activePath === "/pricing" ? "page" : undefined}
            >
                <Text>Pricing</Text>
            </Link>
            <Link
                id="landing-about-link"
                to="/about"
                className={getNavLinkClassName("/about")}
                aria-current={activePath === "/about" ? "page" : undefined}
            >
                <Text>About</Text>
            </Link>
            <Link
                id="landing-contact-link"
                to="/contact"
                className={getNavLinkClassName("/contact")}
                aria-current={activePath === "/contact" ? "page" : undefined}
            >
                <Text>Contact</Text>
            </Link>
        </div>
    );
}
