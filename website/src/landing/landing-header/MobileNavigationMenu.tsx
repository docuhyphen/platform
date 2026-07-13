import {Button, Menu, MenuItem, MenuList, MenuPopover, MenuTrigger, mergeClasses} from "@fluentui/react-components";
import {Navigation24Regular} from "@fluentui/react-icons";
import {LANDING_INDUSTRIES, toNavigationId} from "./landingNavigation.ts";
import {useLandingHeaderStyles} from "./LandingHeaderStyles.tsx";

interface MobileNavigationMenuProps
{
    onNavigate: (to: string) => void;
}

export function MobileNavigationMenu({onNavigate}: MobileNavigationMenuProps)
{
    const styles = useLandingHeaderStyles();

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
                    <MenuItem id="landing-mobile-industries-label">Industries</MenuItem>
                    {LANDING_INDUSTRIES.map((industry) => (
                        <MenuItem
                            key={industry.to}
                            id={`landing-mobile-industry-${toNavigationId(industry.label)}`}
                            onClick={() => onNavigate(industry.to)}
                            className={mergeClasses(styles.mainMobileMenuItem, styles.mainMobileSubMenuItem)}
                        >
                            {industry.label}
                        </MenuItem>
                    ))}
                    <MenuItem
                        id="landing-mobile-pricing"
                        onClick={() => onNavigate("/pricing")}
                        className={styles.mainMobileMenuItem}
                    >
                        Pricing
                    </MenuItem>
                    <MenuItem
                        id="landing-mobile-about"
                        onClick={() => onNavigate("/about")}
                        className={styles.mainMobileMenuItem}
                    >
                        About
                    </MenuItem>
                    <MenuItem
                        id="landing-mobile-contact"
                        onClick={() => onNavigate("/contact")}
                        className={styles.mainMobileMenuItem}
                    >
                        Contact
                    </MenuItem>
                </MenuList>
            </MenuPopover>
        </Menu>
    );
}
