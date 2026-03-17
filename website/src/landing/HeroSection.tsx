import {Button, LargeTitle, Text, makeStyles, mergeClasses, tokens} from "@fluentui/react-components";
import {
    BREAKPOINT_MOBILE,
    BUTTON_MIN_WIDTH,
    SALES_EMAIL_URL,
    SECTION_PADDING_DESKTOP,
    SECTION_PADDING_MOBILE,
    SIGN_UP_URL,
    SPACE_LG,
    SPACE_MD,
    SPACE_XL,
    SPACE_XS,
    WIDTH_CONTENT,
    WIDTH_HERO,
    WIDTH_SUBTITLE,
} from "./shared.ts";

const useStyles = makeStyles({
    wrapper: {
        background: "linear-gradient(135deg, #eef2f7 0%, #ffffff 100%)",
        width: "100%",
        padding: SECTION_PADDING_DESKTOP,
        boxSizing: "border-box",

        [BREAKPOINT_MOBILE]: {
            padding: SECTION_PADDING_MOBILE,
        },
    },

    container: {
        display: "flex",
        flexDirection: "column",
        gap: SPACE_MD,
        maxWidth: WIDTH_CONTENT,
        margin: "0 auto",
        padding: "4.5rem 0",
        textAlign: "center",

        [BREAKPOINT_MOBILE]: {
            padding: `${SPACE_XL} 0`,
        },
    },

    title: {
        backgroundImage: "linear-gradient(90deg,#4B6496 0%,#5482C1 50%,#5EA1E7 100%)",
        backgroundClip: "text",
        WebkitBackgroundClip: "text",
        WebkitTextFillColor: "transparent",
        color: "transparent",
        maxWidth: WIDTH_HERO,
        margin: "0 auto",
        fontSize: tokens.fontSizeHero900,
        lineHeight: tokens.lineHeightHero900,

        [BREAKPOINT_MOBILE]: {
            fontSize: tokens.fontSizeHero800,
            lineHeight: tokens.lineHeightHero800,
        },
    },

    noWrap: {
        whiteSpace: "nowrap",
    },

    supportingText: {
        maxWidth: WIDTH_SUBTITLE,
        textAlign: "center",
        margin: "0 auto",
        fontSize: tokens.fontSizeBase500,
        lineHeight: tokens.lineHeightBase500,
        fontWeight: tokens.fontWeightRegular,
        color: tokens.colorNeutralForeground1,
    },

    actions: {
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
        flexWrap: "wrap",
        gap: SPACE_MD,
        marginTop: SPACE_LG,

        [BREAKPOINT_MOBILE]: {
            flexDirection: "column",
            gap: SPACE_XS,
        },
    },

    buttonBase: {
        minWidth: BUTTON_MIN_WIDTH,
    },

    primaryCta: {
        minWidth: "12rem",
        minHeight: "2.9rem",
        fontSize: tokens.fontSizeBase400,
        lineHeight: tokens.lineHeightBase400,
        fontWeight: tokens.fontWeightSemibold,
        boxShadow: tokens.shadow8,

        [BREAKPOINT_MOBILE]: {
            width: "100%",
            maxWidth: "20rem",
            minHeight: "3rem",
            fontSize: tokens.fontSizeBase500,
            lineHeight: tokens.lineHeightBase500,
        },
    },

    secondaryCta: {
        minWidth: "8.5rem",
        minHeight: "2.5rem",
        fontSize: tokens.fontSizeBase300,
        lineHeight: tokens.lineHeightBase300,

        [BREAKPOINT_MOBILE]: {
            width: "100%",
            maxWidth: "15rem",
            minHeight: "2.6rem",
        },
    },
});

export function HeroSection()
{
    const styles = useStyles();

    return (
        <section className={styles.wrapper}>
            <section className={styles.container}>
                <LargeTitle align="center" className={styles.title}>
                    Welcome to <span className={styles.noWrap}>DocuHyphen</span>, secure document sharing for your
                    sensitive business data
                </LargeTitle>
                <Text className={styles.supportingText}>
                    Request, send, and track sensitive documents with encryption,
                    verification, and full audit trails.
                </Text>

                <div className={styles.actions}>
                    <Button
                        appearance="primary"
                        as="a"
                        size="large"
                        className={mergeClasses(styles.buttonBase, styles.primaryCta)}
                        shape="circular"
                        target="_blank"
                        rel="noopener noreferrer"
                        href={SIGN_UP_URL}
                    >
                        Try it for free
                    </Button>
                    <Text weight="semibold">OR</Text>
                    <Button
                        as="a"
                        appearance="outline"
                        size="medium"
                        className={mergeClasses(styles.buttonBase, styles.secondaryCta)}
                        shape="circular"
                        href={SALES_EMAIL_URL}
                    >
                        Speak to Sales
                    </Button>
                </div>
            </section>
        </section>
    );
}

