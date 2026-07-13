import {makeStyles, tokens} from "@fluentui/react-components";

export const appStyles = makeStyles({
    page: {
        position: "relative",
        display: "flex",
        flexDirection: "column",
        width: "100%",
        overflowX: "clip",
        backgroundColor: tokens.colorNeutralBackground1,
    },

    mainContent: {
        display: "flex",
        flexDirection: "column",
        paddingTop: "4.5rem",
        backgroundColor: tokens.colorNeutralBackground1,
    },

    featuresSurface: {
        backgroundImage: `linear-gradient(180deg, ${tokens.colorNeutralBackground1} 0%, ${tokens.colorBrandBackground2} 48%, ${tokens.colorNeutralBackground1} 100%)`,
    },

    risksSurface: {
        backgroundImage: `linear-gradient(180deg, ${tokens.colorNeutralBackground1} 0%, ${tokens.colorNeutralBackground2} 18%, ${tokens.colorBrandBackground2} 100%)`,

        "& > section": {
            backgroundColor: "transparent",
            backgroundImage: "none",
        },
    },

    audienceSurface: {
        backgroundImage: `linear-gradient(180deg, ${tokens.colorBrandBackground2} 0%, ${tokens.colorNeutralBackground1} 32%, ${tokens.colorNeutralBackground1} 100%)`,
    },
});

