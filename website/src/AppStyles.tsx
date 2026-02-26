import {makeStyles, tokens} from "@fluentui/react-components";

export const appStyles = makeStyles({

    page: {
        position: "relative",
        display: "flex",
        flexDirection: "column",
        gap: "32px",
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


    mainMenuContainer: {

        background: "rgba(255, 255, 255, 0.4)"
    },

    mainMenu: {
        display: "flex",
        flexDirection: "row",
        alignItems: "center",
        width: "960px",
        maxWidth: "100%",
        margin: "0 auto",
        justifyContent: "space-between",
        padding: "16px",
    },

    section1Container: {
        background: `
      linear-gradient(
        180deg,
        #f7efe9 50%,
        #ffffff 100%)`,
        width: "100%",
        padding: 0
    },

    section1Intro: {
        display: "flex",
        flexDirection: "column",
        gap: "16px",
        maxWidth: "960px",
        margin: "0 auto",
        padding: "120px 24px",
        textAlign: "center",

        "@media (max-width: 768px)": {
            padding: "32px 16px",
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
            flex: "0 0 50%",   // don't grow, don't shrink, 40% width
        },

        "& > :last-child": {
            flex: "0 0 50%",   // don't grow, don't shrink, 60% width
        },

        "@media (max-width: 768px)": {
            flexDirection: "column",
            gap: "16px",
            padding: "0 16px",
        },
    },

    section3ContainerWrapper: {
        display: "flex",
        flexDirection: "row",
        gap: "24px",
        maxWidth: "960px",
        margin: "0 auto",
        alignItems: "flex-start",

        "@media (max-width: 768px)": {
            flexDirection: "column",
            gap: "16px",
            padding: "0 16px",
        },
    },

    section3Container: {
        display: "flex",
        flexDirection: "column",
        gap: "12px",

        "& ul": {
            margin: 0,
            padding: 0,
            listStyle: "none",
            display: "flex",
            gap: "28px",

            "& li": {
                padding: "24px",
                borderRadius: "12px"
            },
            "& li:nth-child(1)": {
                backgroundColor: "#F2F7FB",
                borderLeft: "4px solid #5B9BD5"
            },
            "& li:nth-child(2)": {
                backgroundColor: "#F6F4FA",
                borderLeft: "4px solid #8E7CC3"
            },
            "& li:nth-child(3)": {
                backgroundColor: "#F1F9F7",
                borderLeft: "4px solid #4FB3A8"
            },
            "& li:nth-child(4)": {
                backgroundColor: "#FBF7F1",
                borderLeft: "4px solid #C7A76C"
            },
        }

    },

    section3CardTitleContainer: {
        display: "flex",
        gap: "8px",
        minHeight: "48px"
    },

    theSolutionSection: {
        display: "flex",
        flexDirection: "column",
        gap: "16px",
        maxWidth: "960px",
        margin: "0 auto",
        padding: "0 16px",
    },

    cards: {
        display: "grid",
        gridTemplateColumns: "repeat(2, 1fr)",
        gap: "24px",
        "@media (max-width: 768px)": {
            gridTemplateColumns: "1fr",
            gap: "16px",
        },
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
        background: `
      linear-gradient(
        -360deg,
        #f7efe9 0%,
        #ffffff 100%)`,
    },


    sectionTitle: {
        color: "#4c6495",
        fontWeight: "normal",
    },

    forWhoSection: {
        padding: "16px",
        color: "white",
        marginTop: "0",
        display: "flex",
        flexDirection: "column",
        backgroundColor: `${tokens.colorBrandForeground1}`,
    },

    noWrap: {
        whiteSpace: "nowrap",
    },

    btnLong: {
        minWidth: "130px",
    },

    whoForGrid: {
        display: "grid",
        gridTemplateColumns: "repeat(auto-fit, minmax(260px, 1fr))",
        gap: "24px",
        margin: "24px 0",
        justifyItems: "center", // centers cards in their grid cell
    },

    whoForCard: {
        width: "100%",
        maxWidth: "320px", // keeps cards visually consistent
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
});