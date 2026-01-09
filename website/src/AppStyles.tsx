import {makeStyles} from "@fluentui/react-components";

export const appStyles = makeStyles({
    page: {
        display: "flex",
        flexDirection: "column",
        gap: "32px",
        width: "100%",
    },

    heroContainer: {
        background: `
      linear-gradient(
        180deg,
        #f7efe9 50%,
        #ffffff 100%)`,
        width: "100%",
    },

    hero: {
        display: "flex",
        flexDirection: "column",
        gap: "16px",
        maxWidth: "820px",
        margin: "0 auto",
        padding: "48px 24px",
        textAlign: "center",

        "@media (max-width: 768px)": {
            padding: "32px 16px",
        },
    },

    actions: {
        display: "flex",
        justifyContent: "center",
        flexWrap: "wrap",
        gap: "16px",
        marginTop: "24px",
    },

    theProblemSection: {
        display: "flex",
        flexDirection: "row",
        gap: "24px",
        maxWidth: "820px",
        margin: "0 auto",
        alignItems: "flex-start",

        "@media (max-width: 768px)": {
            flexDirection: "column",
            gap: "16px",
            padding: "0 16px",
        },
    },

    theProblemText: {
        display: "flex",
        flexDirection: "column",
        gap: "12px",
    },

    theProblemImg: {
        width: "100%",
        maxWidth: "300px",
        height: "auto",
        filter: "grayscale(50%)",
    },

    theSolutionSection: {
        display: "flex",
        flexDirection: "column",
        gap: "16px",
        maxWidth: "820px",
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

    gradientTitle: {
        backgroundImage: `
      linear-gradient(
        90deg,
        #4B6496 0%,
        #5482C1 50%,
        #5EA1E7 100%
      )
    `,
        backgroundClip: "text",
        WebkitBackgroundClip: "text",
        color: "transparent",
        WebkitTextFillColor: "transparent",
    },

    appLogo: {
        display: "flex",
        justifyContent: "center",
        marginBottom: "16px",
    },

    sectionTitle: {
        color: "#4c6495",
        fontWeight: "normal",
    },

    forWhoSection: {
        textAlign: "center",
        listStyle: "none",
        padding: "0 16px",
        marginTop: "0"
    },
    noWrap: {
        whiteSpace: "nowrap",
    },
    btnLong: {
        minWidth: "130px",
    },
});