import {
    Button,
    Menu,
    MenuItem,
    MenuList,
    MenuPopover,
    MenuTrigger,
    Text,
    makeStyles,
    tokens,
} from "@fluentui/react-components";
import {ChevronDown16Regular, Navigation24Regular} from "@fluentui/react-icons";
import AppLogo from "../app-logo/AppLogo.tsx";
import {
    BREAKPOINT_MOBILE,
    BUTTON_MIN_WIDTH,
    MENU_PADDING_DESKTOP,
    MENU_PADDING_MOBILE,
    SIGN_IN_URL,
    SIGN_UP_URL,
    SPACE_LG,
    SPACE_MD,
    SPACE_SM,
    WIDTH_CONTENT,
} from "./shared.ts";
import {Link, useNavigate} from "react-router-dom";

type NavLink = {
    label: string;
    to: string;
};

const SOLUTIONS: NavLink[] = [
    {label: "Real Estate", to: "/solutions/real-estate"},
    {label: "Legal", to: "/solutions/legal"},
    {label: "Healthcare", to: "/solutions/healthcare"},
    {label: "Accounting", to: "/solutions/accounting"},
    {label: "Banking", to: "/solutions/banking"},
];

const RESOURCES: NavLink[] = [
    {label: "Overview", to: "/resources"},
    {label: "IdP Setup Guide", to: "/help/idp-setup"},
    {label: "Security FAQ", to: "/help/security-faq"},
    {label: "Security & Trust", to: "/security"},
];

const useStyles = makeStyles({
    wrapper: {
        background: tokens.colorNeutralBackground1,
        padding: MENU_PADDING_DESKTOP,
        boxSizing: "border-box",
        borderBottom: `1px solid ${tokens.colorNeutralStroke2}`,
        position: "sticky",
        top: 0,
        zIndex: 50,

        [BREAKPOINT_MOBILE]: {
            padding: MENU_PADDING_MOBILE,
        },
    },

    nav: {
        display: "flex",
        flexDirection: "row",
        alignItems: "center",
        width: WIDTH_CONTENT,
        maxWidth: "100%",
        margin: "0 auto",
        justifyContent: "space-between",
        padding: `${SPACE_MD} 0`,
        gap: SPACE_MD,
    },

    leftGroup: {
        display: "flex",
        alignItems: "center",
        gap: SPACE_LG,
        flex: 1,
        minWidth: 0,
    },

    logo: {
        display: "inline-flex",
        flexShrink: 0,
    },

    desktopLinks: {
        display: "flex",
        alignItems: "center",
        gap: SPACE_LG,

        "@media (max-width: 56em)": {
            display: "none",
        },
    },

    rightActions: {
        display: "flex",
        alignItems: "center",
        gap: SPACE_SM,
        flexShrink: 0,
    },

    navLink: {
        display: "inline-flex",
        alignItems: "center",
        gap: "0.2rem",
        color: tokens.colorNeutralForeground1,
        textDecorationLine: "none",
        fontSize: tokens.fontSizeBase300,
        fontWeight: tokens.fontWeightSemibold,
        padding: "0.4rem 0.2rem",
        background: "transparent",
        border: "none",
        cursor: "pointer",

        ":hover": {
            color: tokens.colorBrandForeground1,
        },
    },

    signInButton: {
        minWidth: BUTTON_MIN_WIDTH,

        "@media (max-width: 36em)": {
            display: "none",
        },
    },

    tryFreeButton: {
        minWidth: BUTTON_MIN_WIDTH,

        [BREAKPOINT_MOBILE]: {
            minWidth: "auto",
            paddingLeft: SPACE_MD,
            paddingRight: SPACE_MD,
        },
    },

    mobileMenuButton: {
        display: "none",

        "@media (max-width: 56em)": {
            display: "inline-flex",
        },
    },
});

export function LandingHeader()
{
    const styles = useStyles();
    const navigate = useNavigate();

    return (
        <header className={styles.wrapper}>
            <nav className={styles.nav} aria-label="Primary">
                <div className={styles.leftGroup}>
                    <Link to="/" aria-label="DocuHyphen home" className={styles.logo}>
                        <AppLogo/>
                    </Link>

                    <div className={styles.desktopLinks}>
                        <Menu>
                            <MenuTrigger disableButtonEnhancement>
                                <button className={styles.navLink} type="button">
                                    Solutions <ChevronDown16Regular/>
                                </button>
                            </MenuTrigger>
                            <MenuPopover>
                                <MenuList>
                                    {SOLUTIONS.map((s) => (
                                        <MenuItem key={s.to} onClick={() => navigate(s.to)}>
                                            {s.label}
                                        </MenuItem>
                                    ))}
                                </MenuList>
                            </MenuPopover>
                        </Menu>

                        <Link to="/pricing" className={styles.navLink}>
                            <Text weight="semibold">Pricing</Text>
                        </Link>

                        <Menu>
                            <MenuTrigger disableButtonEnhancement>
                                <button className={styles.navLink} type="button">
                                    Resources <ChevronDown16Regular/>
                                </button>
                            </MenuTrigger>
                            <MenuPopover>
                                <MenuList>
                                    {RESOURCES.map((r) => (
                                        <MenuItem key={r.to} onClick={() => navigate(r.to)}>
                                            {r.label}
                                        </MenuItem>
                                    ))}
                                </MenuList>
                            </MenuPopover>
                        </Menu>
                    </div>
                </div>

                <div className={styles.rightActions}>
                    <Button
                        appearance="subtle"
                        as="a"
                        className={styles.signInButton}
                        target="_blank"
                        rel="noopener noreferrer"
                        shape="circular"
                        href={SIGN_IN_URL}
                    >
                        Sign in
                    </Button>
                    <Button
                        appearance="primary"
                        as="a"
                        className={styles.tryFreeButton}
                        target="_blank"
                        rel="noopener noreferrer"
                        shape="circular"
                        href={SIGN_UP_URL}
                    >
                        Try it free
                    </Button>

                    <Menu>
                        <MenuTrigger disableButtonEnhancement>
                            <Button
                                appearance="subtle"
                                shape="circular"
                                icon={<Navigation24Regular/>}
                                aria-label="Open menu"
                                className={styles.mobileMenuButton}
                            />
                        </MenuTrigger>
                        <MenuPopover>
                            <MenuList>
                                <MenuItem onClick={() => navigate("/pricing")}>Pricing</MenuItem>
                                <MenuItem onClick={() => navigate("/resources")}>Resources</MenuItem>
                                <MenuItem onClick={() => navigate("/security")}>Security</MenuItem>
                                <MenuItem onClick={() => navigate("/about")}>About</MenuItem>
                                <MenuItem onClick={() => navigate("/contact")}>Contact</MenuItem>
                                <MenuItem disabled>Solutions</MenuItem>
                                {SOLUTIONS.map((s) => (
                                    <MenuItem key={s.to} onClick={() => navigate(s.to)}>
                                        &nbsp;&nbsp;{s.label}
                                    </MenuItem>
                                ))}
                                <MenuItem
                                    onClick={() => window.open(SIGN_IN_URL, "_blank", "noopener,noreferrer")}
                                >
                                    Sign in
                                </MenuItem>
                            </MenuList>
                        </MenuPopover>
                    </Menu>
                </div>
            </nav>
        </header>
    );
}
