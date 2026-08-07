import {Button, Menu, MenuItem, MenuList, MenuPopover, MenuTrigger, mergeClasses} from "@fluentui/react-components";
import {Navigation24Regular} from "@fluentui/react-icons";
import {LANDING_INDUSTRIES, toNavigationId} from "./landingNavigation.ts";
import {useLandingHeaderStyles} from "./LandingHeaderStyles.tsx";

interface MobileNavigationMenuProps
{
    activePath: string;
    onNavigate: (to: string) => void;
}

export function MobileNavigationMenu({activePath, onNavigate}: MobileNavigationMenuProps)
{
    const styles = useLandingHeaderStyles();
    const industriesActive = activePath.startsWith("/industries");
    const getMenuItemClassName = (to: string) =>
        mergeClasses(styles.mainMobileMenuItem, activePath === to && styles.activeMenuItem);
    const getSubMenuItemClassName = (to: string) =>
        mergeClasses(
            styles.mainMobileMenuItem,
            styles.mainMobileSubMenuItem,
            activePath === to && styles.activeSubMenuItem,
        );

    return (
        <Menu>
            <MenuTrigger disableButtonEnhancement>
                <Button
                    id="landing-mobile-menu-trigger"
                    appearance="subtle"
                    shape="circular"
                    icon={<Navigation24Regular/>}
                    aria-label="Open menu"
                    className={styles.mobileMenuButton}
                />
            </MenuTrigger>
            <MenuPopover>
                <MenuList
                    id="landing-mobile-menu"
                    className={styles.mainMobileMenu}
                >
                    <MenuItem
                        id="landing-mobile-industries-label"
                        className={mergeClasses(styles.mainMobileMenuItem, industriesActive && styles.activeMenuItem)}
                        aria-current={industriesActive ? "page" : undefined}
                    >
                        Industries
                    </MenuItem>
                    {LANDING_INDUSTRIES.map((industry) => (
                        <MenuItem
                            key={industry.to}
                            id={`landing-mobile-industry-${toNavigationId(industry.label)}`}
                            onClick={() => onNavigate(industry.to)}
                            className={getSubMenuItemClassName(industry.to)}
                            aria-current={activePath === industry.to ? "page" : undefined}
                        >
                            {industry.label}
                        </MenuItem>
                    ))}
                    <MenuItem
                        id="landing-mobile-pricing"
                        onClick={() => onNavigate("/pricing")}
                        className={getMenuItemClassName("/pricing")}
                        aria-current={activePath === "/pricing" ? "page" : undefined}
                    >
                        Pricing
                    </MenuItem>
                    {/* About menu item hidden while the page is pending redesign */}
                    <MenuItem
                        id="landing-mobile-contact"
                        onClick={() => onNavigate("/contact")}
                        className={getMenuItemClassName("/contact")}
                        aria-current={activePath === "/contact" ? "page" : undefined}
                    >
                        Contact
                    </MenuItem>
                </MenuList>
            </MenuPopover>
        </Menu>
    );
}
