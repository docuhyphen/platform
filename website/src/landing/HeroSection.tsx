import {Button, LargeTitle, Text, makeStyles, mergeClasses, tokens} from "@fluentui/react-components";
import {
    BREAKPOINT_MOBILE,
    BUTTON_MIN_WIDTH,
    SECTION_PADDING_DESKTOP,
    SECTION_PADDING_MOBILE,
    SIGN_UP_URL,
    SPACE_LG,
    SPACE_MD,
    SPACE_XS,
    WIDTH_CONTENT,
} from "./shared.ts";
import {SpeakToSalesDialog} from "./SpeakToSalesDialog.tsx";
import {FloatingDocuments} from "./FloatingDocuments.tsx";

const useStyles = makeStyles({
    wrapper: {
        background: "linear-gradient(135deg, #eef2f7 0%, #ffffff 100%)",
        width: "100%",
        padding: SECTION_PADDING_DESKTOP,
        boxSizing: "border-box",
        position: "relative",
        overflow: "hidden",

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
        position: "relative",
        zIndex: 1,

        [BREAKPOINT_MOBILE]: {
            padding: `3rem 0`,
        },
    },

    title: {
        backgroundImage: "linear-gradient(90deg,#4B6496 0%,#5482C1 50%,#5EA1E7 100%)",
        backgroundClip: "text",
        WebkitBackgroundClip: "text",
        WebkitTextFillColor: "transparent",
        color: "transparent",
        // maxWidth: WIDTH_HERO,
        maxWidth: "58rem",
        // margin: "0 auto",
        fontSize: "4rem",
        fontWeight: tokens.fontWeightSemibold,
        lineHeight: "5rem",
        padding: "4px 0",

        [BREAKPOINT_MOBILE]: {
            fontSize: tokens.fontSizeHero800,
            lineHeight: tokens.lineHeightHero800,
        },
    },

    noWrap: {
        whiteSpace: "nowrap",
    },

    typingCursor: {
        display: "inline-block",
        marginLeft: "0.2rem",
        fontSize: "1.45em",
        fontWeight: tokens.fontWeightBold,
        lineHeight: 1,
        color: "#4b6496",
        WebkitTextFillColor: "#4b6496",
        verticalAlign: "baseline",
        animationName: {
            "0%": {opacity: 1},
            "45%": {opacity: 1},
            "55%": {opacity: 0},
            "100%": {opacity: 0},
        },
        animationDuration: "420ms",
        animationTimingFunction: "ease-in-out",
        animationIterationCount: "infinite",
        animationDirection: "alternate",
    },

    typingCursorHidden: {
        display: "none",
    },

    supportingText: {
        // maxWidth: WIDTH_SUBTITLE,
        maxWidth: "48rem",
        // textAlign: "center",
        // margin: "0 auto",
        fontSize: tokens.fontSizeBase500,
        lineHeight: tokens.lineHeightBase500,
        fontWeight: tokens.fontWeightRegular,
        color: tokens.colorNeutralForeground1,
    },

    supportingHidden: {
        opacity: 0,
        transform: "translateY(0.4rem)",
        transition: "opacity 280ms ease, transform 280ms ease",
    },

    supportingVisible: {
        opacity: 1,
        transform: "translateY(0)",
    },

    actions: {
        display: "flex",
        justifyContent: "left",
        alignItems: "center",
        flexWrap: "wrap",
        gap: SPACE_MD,
        marginTop: SPACE_LG,

        [BREAKPOINT_MOBILE]: {
            flexDirection: "row",
            gap: SPACE_XS,
        },
    },

    actionsHidden: {
        opacity: 0,
        transform: "translateY(0.4rem)",
        pointerEvents: "none",
        transition: "opacity 280ms ease, transform 280ms ease",
    },

    actionsVisible: {
        opacity: 1,
        transform: "translateY(0)",
        pointerEvents: "auto",
    },

    trustLine: {
        marginTop: SPACE_MD,
        color: tokens.colorNeutralForeground3,
        fontSize: tokens.fontSizeBase200,
        letterSpacing: "0.02em",
    },

    buttonBase: {
        minWidth: BUTTON_MIN_WIDTH,
    },

    primaryCta: {
        minWidth: "12rem",
        minHeight: "2.5rem",
        fontSize: tokens.fontSizeBase400,
        lineHeight: tokens.lineHeightBase400,
        fontWeight: tokens.fontWeightSemibold,
        boxShadow: tokens.shadow8,

        [BREAKPOINT_MOBILE]: {
            width: "100%",
            maxWidth: "8rem",
            minWidth: "0",
            minHeight: "2rem",
            fontSize: tokens.fontSizeBase400,
            lineHeight: tokens.lineHeightBase400,
        },
    },

    secondaryCta: {
        minWidth: "12rem",
        minHeight: "2.5rem",
        fontSize: tokens.fontSizeBase300,
        lineHeight: tokens.lineHeightBase300,

        [BREAKPOINT_MOBILE]: {
            width: "100%",
            maxWidth: "8rem",
            minWidth: "0",
        },
    },
});

export function HeroSection()
{
    const styles = useStyles();

    return (
        <section className={styles.wrapper}>
            <FloatingDocuments/>
            <section className={styles.container}>
                <LargeTitle className={styles.title}>
                    Streamline your document exchange process
                </LargeTitle>
                <Text className={styles.supportingText}>
                    Exchange documents, automate approvals, and streamline collaboration across teams and organizations.
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
                        Start Free
                    </Button>
                    <Text weight="semibold">OR</Text>
                    <SpeakToSalesDialog
                        trigger={
                            <Button
                                appearance="secondary"
                                size="medium"
                                className={mergeClasses(styles.buttonBase, styles.secondaryCta)}
                                shape="circular"
                            >
                                Speak to Sales
                            </Button>
                        }
                    />
                </div>
            </section>
        </section>
    );
}
