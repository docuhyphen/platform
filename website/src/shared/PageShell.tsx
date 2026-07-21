import type {ReactNode} from "react";
import {makeStyles, tokens} from "@fluentui/react-components";
import {LandingHeader} from "../landing/landing-header/LandingHeader.tsx";
import {Footer} from "./Footer.tsx";
import {
    BREAKPOINT_MOBILE,
    SECTION_PADDING_DESKTOP,
    SECTION_PADDING_MOBILE,
    SPACE_LG,
    WIDTH_CONTENT,
} from "../landing/shared.ts";

const useStyles = makeStyles({
    page: {
        position: "relative",
        display: "flex",
        flexDirection: "column",
        minHeight: "100vh",
        width: "100%",
        backgroundColor: "#f8faff",
    },

    main: {
        flex: 1,
        width: "100%",
        paddingTop: "4.5rem",
        boxSizing: "border-box",
    },

    content: {
        maxWidth: WIDTH_CONTENT,
        margin: "0 auto",
        padding: SECTION_PADDING_DESKTOP,
        boxSizing: "border-box",
        display: "flex",
        flexDirection: "column",
        gap: SPACE_LG,

        [BREAKPOINT_MOBILE]: {
            padding: SECTION_PADDING_MOBILE,
        },
    },

    eyebrow: {
        color: tokens.colorBrandForeground1,
        fontSize: tokens.fontSizeBase200,
        fontWeight: tokens.fontWeightSemibold,
        letterSpacing: "0.08em",
        textTransform: "uppercase",
    },
});

type PageShellProps = {
    children: ReactNode;
    constrain?: boolean;
};

export function PageShell({children, constrain = true}: PageShellProps)
{
    const styles = useStyles();

    return (
        <div className={styles.page}>
            <LandingHeader fixed/>
            <main className={styles.main}>
                {constrain ? <div className={styles.content}>{children}</div> : children}
            </main>
            <Footer/>
        </div>
    );
}
