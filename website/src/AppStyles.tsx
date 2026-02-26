import {makeStyles, tokens} from "@fluentui/react-components";

export const appStyles = makeStyles({

    page: {
        position: "relative",
        display: "flex",
        flexDirection: "column",
        width: "100%",
        overflow: "hidden",
        backgroundColor: "#f8faff",

        // Main radial glow layer
        "::before": {
            content: '""',
            position: "absolute",
            inset: "-25%",
            background: `
        radial-gradient(circle at -1% 20%, rgb(94, 161, 231) 0%, transparent 40%), 
        radial-gradient(circle at 70% 40%, rgb(84, 130, 193) 0%, transparent 16%), 
        radial-gradient(circle at 50% 80%, rgb(75, 100, 150) 0%, transparent 17%)
      `,
            filter: "blur(140px) saturate(110%)",
            opacity: 0.35,
            zIndex: 0,
        },

        // Ensure content sits above glow
        "> *": {
            position: "relative",
            zIndex: 1,
        },
    },

    sectionContainerWrapper: {

        padding: "24px 32px",
        boxSizing: "border-box",

        "@media (max-width: 768px)": {
            padding: "16px 32x",
        },
    },

    mainMenuContainer: {

        background: "rgba(255, 255, 255, 0.6)",
        padding: "0 32px",
        boxSizing: "border-box",

        "@media (max-width: 768px)": {
            padding: "0px 16px",
        },
    },

    mainMenu: {
        display: "flex",
        flexDirection: "row",
        alignItems: "center",
        width: "960px",
        maxWidth: "100%",
        margin: "0 auto",
        justifyContent: "space-between",
        padding: "16px 0"
    },

    section1ContainerWrapper: {
        background: `linear-gradient(180deg, #f7efe9 50%, #ffffff 100%)`,
        width: "100%",
    },

    section1Container: {
        display: "flex",
        flexDirection: "column",
        gap: "16px",
        maxWidth: "960px",
        margin: "0 auto",
        padding: "120px 0px",
        textAlign: "center",

        "@media (max-width: 768px)": {
            padding: "32px 0px",
        },
    },

    section1Title: {
        backgroundImage: `linear-gradient(90deg,#4B6496 0%,#5482C1 50%,#5EA1E7 100%)`,
        backgroundClip: "text",
        WebkitBackgroundClip: "text",
        WebkitTextFillColor: "transparent",
        color: "transparent",

        fontSize: tokens.fontSizeHero800,
        lineHeight: tokens.lineHeightHero800,

        "@media (max-width: 768px)": {
            fontSize: tokens.fontSizeHero700,
            lineHeight: tokens.lineHeightHero700,
        },
    },

    section1Actions: {
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
        flexWrap: "wrap",
        gap: "16px",
        marginTop: "24px",

        "@media (max-width: 768px)": {
            flexDirection: "column",
            gap: "8px"
        },
    },

    section2ContainerWrapper: {

        display: "flex",
        flexDirection: "row",
        gap: "24px",
        maxWidth: "960px",
        margin: "0 auto",
        alignItems: "flex-start",
    },

    section2Container: {

        display: "flex",
        alignItems:"center",
        "& > :first-child": {
            flex: "0 0 50%",
        },

        "& > :last-child": {
            flex: "0 0 50%",
        },

        "@media (max-width: 768px)": {
            flexDirection: "column",
            gap: "16px",
        },
    },

    section3ContainerWrapper: {
        display: "flex",
        flexDirection: "row",
        maxWidth: "960px",
        margin: "0 auto",
        alignItems: "flex-start",

        "@media (max-width: 768px)": {
            flexDirection: "column",
            gap: "16px",
        },
    },

    section3Container: {

        display: "grid",
        gap: "24px",
        gridTemplateColumns: "repeat(2, 1fr)",

        "@media (max-width: 768px)": {
            gridTemplateColumns: "1fr",
            gap: "16px",
        },
    },

    section3Card: {
        padding: "24px",
        borderRadius: "12px"
    },

    section3Card1: {

        backgroundColor: "#F2F7FB",
        borderLeft: "4px solid #5B9BD5"
    },

    section3Card2: {

        backgroundColor: "#F6F4FA",
        borderLeft: "4px solid #8E7CC3"
    },

    section3Card3: {

        backgroundColor: "#F1F9F7",
        borderLeft: "4px solid #4FB3A8"
    },

    section3Card4: {

        backgroundColor: "#FBF7F1",
        borderLeft: "4px solid #C7A76C"
    },

    section3CardTitleContainer: {
        display: "flex",
        gap: "8px",
        minHeight: "48px"
    },

    section4ContainerWrapper: {

        display: "flex",
        gap: "24px",
        maxWidth: "960px",
        margin: "0 auto",
        alignItems: "flex-start",
    },

    section4Container: {
        flexDirection: "column",
        display: "flex",
        "& > :first-child": {
            flex: "0 0 50%",
        },

        "& > :last-child": {
            flex: "0 0 50%",
        },

        "@media (max-width: 768px)": {
            flexDirection: "column",
            gap: "16px",
        },
    },

    section5ContainerWrapper: {
        display: "flex",
        flexDirection: "column",
        gap: "16px",
    },

    section5Container: {
        margin: "0 auto",
        maxWidth: "960px",
        display: "grid",
        gridTemplateColumns: "repeat(2, 1fr)",
        gap: "24px",
        "@media (max-width: 768px)": {
            gridTemplateColumns: "1fr",
            gap: "16px",
        },
    },

    section6ContainerWrapper: {
        backgroundColor: `${tokens.colorBrandForeground1}`,
    },

    section6Container: {
        margin: "0 auto",
        maxWidth: "960px",
        color: "white",
        display: "flex",
        flexDirection: "column",
    },

    noWrap: {
        whiteSpace: "nowrap",
    },

    btnLong: {
        minWidth: "130px",
    },

    whoForGrid: {
        display: "grid",
        gap: "24px",
        margin: "24px 0",
        justifyItems: "center",
        gridTemplateColumns: "repeat(2, 1fr)",

        "@media (max-width: 768px)": {
            gridTemplateColumns: "1fr",
            gap: "16px",
        },
        // "@media (min-width: 640px)": {
        //     gridTemplateColumns: "repeat(2, 1fr)",
        // },
        //
        // "@media (min-width: 1200px)": {
        //     gridTemplateColumns: "repeat(4, 1fr)",
        // },
    },

    whoForCard: {
        width: "100%",
        // maxWidth: "320px", // keeps cards visually consistent
    },

    whoForList: {
        marginTop: "8px",
        paddingLeft: "16px",

        display: "flex",
        flexDirection: "column",
        gap: "12px",

        "& li": {
            display: "flex",
            flexDirection: "column",
            gap: "4px",
        },
    },

    whoForSubtitle: {
        textAlign: "center",
        maxWidth: "700px",
        margin: "0 auto",
    },

    solutionSection: {
        display: "grid",
        gridTemplateColumns: "repeat(2, 1fr)",
        gap: "24px",
        "@media (max-width: 768px)": {
            gridTemplateColumns: "1fr",
            gap: "16px",
        },
    },

    footerCta: {
        textAlign: "center",
        padding: "48px 16px",
        background: `linear-gradient(-360deg, #f7efe9 0%, #ffffff 100%)`,
    },

    sectionTitle: {
        color: tokens.colorBrandForeground1,
        fontWeight: tokens.fontWeightSemibold,

        // "@media (max-width: 768px)": {
        //     fontSize: "1.5rem",
        //     lineHeight: "1.5rem"
        // }
    },
});