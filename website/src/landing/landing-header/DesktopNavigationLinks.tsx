import {Button, Menu, MenuItem, MenuList, MenuPopover, MenuTrigger, Text, mergeClasses} from "@fluentui/react-components";
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
        <div id={"landing-desktop-links"}
             className={styles.desktopLinks}>
            <Menu>
                <MenuTrigger disableButtonEnhancement>
                    <Button id={"landing-industries-menu-trigger"}
                            appearance="transparent"
                            shape="circular"
                            className={mergeClasses(styles.navLink, styles.navMenuButton, industriesActive && styles.activeNavLink)}
                            icon={<ChevronDown16Regular id={"landing-industries-menu-trigger-icon"}/>}
                            iconPosition="after"
                            aria-current={industriesActive ? "page" : undefined}>
                        Industries
                    </Button>
                </MenuTrigger>
                <MenuPopover id={"landing-industries-menu-popover"}>
                    <MenuList id={"landing-industries-menu-list"}>
                        {LANDING_INDUSTRIES.map((industry) => (
                            <MenuItem key={industry.to}
                                      id={`landing-industry-${toNavigationId(industry.label)}`}
                                      onClick={() => onNavigate(industry.to)}
                                      className={activePath === industry.to ? styles.activeSubMenuItem : undefined}
                                      aria-current={activePath === industry.to ? "page" : undefined}>
                                {industry.label}
                            </MenuItem>
                        ))}
                    </MenuList>
                </MenuPopover>
            </Menu>

            <Link id={"landing-pricing-link"}
                  to="/pricing"
                  className={getNavLinkClassName("/pricing")}
                  aria-current={activePath === "/pricing" ? "page" : undefined}>
                <Text id={"landing-pricing-link-text"}>Pricing</Text>
            </Link>
            <Link id={"landing-contact-link"}
                  to="/#home-contact-info"
                  className={styles.navLink}>
                <Text id={"landing-contact-link-text"}>Contact</Text>
            </Link>
        </div>
    );
}
