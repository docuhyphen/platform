import {Menu, MenuItem, MenuList, MenuPopover, MenuTrigger, Text} from "@fluentui/react-components";
import {ChevronDown16Regular} from "@fluentui/react-icons";
import {Link} from "react-router-dom";
import {LANDING_INDUSTRIES, toNavigationId} from "./landingNavigation.ts";
import {useLandingHeaderStyles} from "./LandingHeaderStyles.tsx";

interface DesktopNavigationLinksProps
{
    onNavigate: (to: string) => void;
}

export function DesktopNavigationLinks({onNavigate}: DesktopNavigationLinksProps)
{
    const styles = useLandingHeaderStyles();

    return (
        <div
            id="landing-desktop-links"
            className={styles.desktopLinks}
        >
            <Menu>
                <MenuTrigger disableButtonEnhancement>
                    <button
                        id="landing-industries-menu-trigger"
                        className={styles.navLink}
                        type="button"
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
                className={styles.navLink}
            >
                <Text>Pricing</Text>
            </Link>
            <Link
                id="landing-about-link"
                to="/about"
                className={styles.navLink}
            >
                <Text>About</Text>
            </Link>
            <Link
                id="landing-contact-link"
                to="/contact"
                className={styles.navLink}
            >
                <Text>Contact</Text>
            </Link>
        </div>
    );
}
