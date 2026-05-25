import {Text, makeStyles, tokens} from "@fluentui/react-components";
import {
    ShieldCheckmark20Regular,
    LockClosed20Regular,
    Globe20Regular,
} from "@fluentui/react-icons";
import {Link} from "react-router-dom";
import AppLogo from "../app-logo/AppLogo.tsx";
import {
    BREAKPOINT_MOBILE,
    SALES_EMAIL_URL,
    SIGN_IN_URL,
    SIGN_UP_URL,
    SPACE_LG,
    SPACE_MD,
    SPACE_SM,
    SPACE_XS,
    WIDTH_CONTENT,
} from "../landing/shared.ts";

type FooterLink = {
    label: string;
    to?: string;
    href?: string;
    external?: boolean;
};

type FooterColumn = {
    title: string;
    links: FooterLink[];
};

const useStyles = makeStyles({
    wrapper: {
        backgroundColor: tokens.colorNeutralBackground1,
        borderTop: `1px solid ${tokens.colorNeutralStroke2}`,
        marginTop: "auto",
    },

    container: {
        maxWidth: WIDTH_CONTENT,
        margin: "0 auto",
        padding: `${SPACE_LG} 2rem`,
        boxSizing: "border-box",
        display: "flex",
        flexDirection: "column",
        gap: SPACE_LG,

        [BREAKPOINT_MOBILE]: {
            padding: `${SPACE_LG} 1rem`,
        },
    },

    topRow: {
        display: "grid",
        gridTemplateColumns: "1.4fr repeat(5, 1fr)",
        gap: SPACE_LG,

        "@media (max-width: 64em)": {
            gridTemplateColumns: "1fr 1fr 1fr",
            gap: SPACE_MD,
        },

        [BREAKPOINT_MOBILE]: {
            gridTemplateColumns: "1fr 1fr",
            gap: SPACE_MD,
        },
    },

    brand: {
        display: "flex",
        flexDirection: "column",
        gap: SPACE_SM,
        gridColumn: "span 1",

        [BREAKPOINT_MOBILE]: {
            gridColumn: "span 2",
        },
    },

    brandTagline: {
        color: tokens.colorNeutralForeground2,
        fontSize: tokens.fontSizeBase200,
        lineHeight: tokens.lineHeightBase300,
        maxWidth: "16rem",
    },

    column: {
        display: "flex",
        flexDirection: "column",
        gap: SPACE_XS,
    },

    columnTitle: {
        color: tokens.colorNeutralForeground1,
        fontSize: tokens.fontSizeBase200,
        fontWeight: tokens.fontWeightSemibold,
        letterSpacing: "0.06em",
        textTransform: "uppercase",
        marginBottom: SPACE_XS,
    },

    link: {
        color: tokens.colorNeutralForeground2,
        fontSize: tokens.fontSizeBase300,
        textDecorationLine: "none",

        ":hover": {
            color: tokens.colorBrandForeground1,
            textDecorationLine: "underline",
        },
    },

    trustStrip: {
        display: "flex",
        flexWrap: "wrap",
        alignItems: "center",
        gap: SPACE_LG,
        padding: `${SPACE_MD} 0`,
        borderTop: `1px solid ${tokens.colorNeutralStroke2}`,
        borderBottom: `1px solid ${tokens.colorNeutralStroke2}`,
    },

    badge: {
        display: "flex",
        alignItems: "center",
        gap: SPACE_XS,
        color: tokens.colorNeutralForeground2,
        fontSize: tokens.fontSizeBase200,
    },

    badgeIcon: {
        color: tokens.colorBrandForeground1,
    },

    statusPill: {
        display: "inline-block",
        backgroundColor: tokens.colorNeutralBackground3,
        color: tokens.colorNeutralForeground3,
        padding: "2px 8px",
        borderRadius: "999px",
        fontSize: "0.7rem",
        fontWeight: tokens.fontWeightSemibold,
        textTransform: "uppercase",
        letterSpacing: "0.05em",
    },

    bottomRow: {
        display: "flex",
        flexWrap: "wrap",
        justifyContent: "space-between",
        alignItems: "center",
        gap: SPACE_MD,
    },

    copy: {
        color: tokens.colorNeutralForeground3,
        fontSize: tokens.fontSizeBase200,
    },
});

const columns: FooterColumn[] = [
    {
        title: "Product",
        links: [
            {label: "Features", to: "/#features"},
            {label: "Pricing", to: "/pricing"},
            {label: "Security", to: "/security"},
            {label: "Sign in", href: SIGN_IN_URL, external: true},
            {label: "Try it free", href: SIGN_UP_URL, external: true},
        ],
    },
    {
        title: "Solutions",
        links: [
            {label: "Real Estate", to: "/solutions/real-estate"},
            {label: "Legal", to: "/solutions/legal"},
            {label: "Healthcare", to: "/solutions/healthcare"},
            {label: "Accounting", to: "/solutions/accounting"},
            {label: "Banking", to: "/solutions/banking"},
        ],
    },
    {
        title: "Resources",
        links: [
            {label: "All resources", to: "/resources"},
            {label: "IdP Setup Guide", to: "/help/idp-setup"},
            {label: "Security FAQ", to: "/help/security-faq"},
        ],
    },
    {
        title: "Company",
        links: [
            {label: "About", to: "/about"},
            {label: "Contact", to: "/contact"},
            {label: "Sales", href: SALES_EMAIL_URL, external: true},
        ],
    },
    {
        title: "Legal",
        links: [
            {label: "Privacy", to: "/legal/privacy"},
            {label: "Terms", to: "/legal/terms"},
            {label: "DPA", to: "/legal/dpa"},
        ],
    },
];

export function Footer()
{
    const styles = useStyles();

    return (
        <footer className={styles.wrapper}>
            <div className={styles.container}>
                <div className={styles.topRow}>
                    <div className={styles.brand}>
                        <AppLogo/>
                        <Text className={styles.brandTagline}>
                            Secure document sharing for regulated teams ,
                            built for control, transparency, and audit.
                        </Text>
                    </div>

                    {/*{columns.map((col) => (*/}
                    {/*    <div key={col.title} className={styles.column}>*/}
                    {/*        <Text className={styles.columnTitle}>{col.title}</Text>*/}
                    {/*        {col.links.map((link) =>*/}
                    {/*            link.to ? (*/}
                    {/*                <Link key={link.label} to={link.to} className={styles.link}>*/}
                    {/*                    {link.label}*/}
                    {/*                </Link>*/}
                    {/*            ) : (*/}
                    {/*                <a*/}
                    {/*                    key={link.label}*/}
                    {/*                    href={link.href}*/}
                    {/*                    target={link.external ? "_blank" : undefined}*/}
                    {/*                    rel={link.external ? "noopener noreferrer" : undefined}*/}
                    {/*                    className={styles.link}*/}
                    {/*                >*/}
                    {/*                    {link.label}*/}
                    {/*                </a>*/}
                    {/*            )*/}
                    {/*        )}*/}
                    {/*    </div>*/}
                    {/*))}*/}
                </div>

                {/*<div className={styles.trustStrip}>*/}
                {/*    <span className={styles.badge}>*/}
                {/*        <ShieldCheckmark20Regular className={styles.badgeIcon}/>*/}
                {/*        SOC 2 Type II*/}
                {/*        <span className={styles.statusPill}>In progress</span>*/}
                {/*    </span>*/}
                {/*    <span className={styles.badge}>*/}
                {/*        <LockClosed20Regular className={styles.badgeIcon}/>*/}
                {/*        AES-256 encryption at rest*/}
                {/*    </span>*/}
                {/*    <span className={styles.badge}>*/}
                {/*        <Globe20Regular className={styles.badgeIcon}/>*/}
                {/*        GDPR &amp; POPIA aligned*/}
                {/*    </span>*/}
                {/*</div>*/}

                <div className={styles.bottomRow}>
                    <Text className={styles.copy}>
                        &copy; {new Date().getFullYear()} DocuHyphen. All rights reserved.
                    </Text>
                    {/*<Text className={styles.copy}>*/}
                    {/*    Made for teams in finance, legal, healthcare &amp; real estate, and more.*/}
                    {/*</Text>*/}
                </div>
            </div>
        </footer>
    );
}
