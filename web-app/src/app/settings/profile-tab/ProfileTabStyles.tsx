import {makeStyles, shorthands, tokens} from "@fluentui/react-components";

export const useProfileTabStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        gap: "20px",
        width: "100%",
        minWidth: 0,
        maxWidth: "1120px",
    },

    cardGrid: {
        display: "grid",
        gridTemplateColumns: "repeat(2, minmax(0, 1fr))",
        gap: "16px",
        alignItems: "stretch",
        "@media (max-width: 900px)": {
            gridTemplateColumns: "1fr",
        },
    },

    infoStack: {
        display: "flex",
        flexDirection: "column",
        gap: "16px",
    },

    infoRow: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "flex-start",
        gap: "16px",
        flexWrap: "wrap",
    },

    infoLabel: {
        color: tokens.colorNeutralForeground3,
    },

    contentBlock: {
        display: "flex",
        flexDirection: "column",
        gap: "6px",
        minWidth: 0,
    },

    helperText: {
        color: tokens.colorNeutralForeground3,
    },

    securityStack: {
        display: "flex",
        flexDirection: "column",
        gap: "12px",
    },

    securityPanel: {
        display: "flex",
        justifyContent: "space-between",
        gap: "16px",
        alignItems: "center",
        flexWrap: "wrap",
    },

    securityNote: {
        display: "flex",
        alignItems: "flex-start",
        gap: "10px",
        ...shorthands.padding("12px", "14px"),
        ...shorthands.borderRadius(tokens.borderRadiusLarge),
        ...shorthands.border("1px", "solid", tokens.colorNeutralStroke2),
        color: tokens.colorNeutralForeground2,
    },

    notificationCardContent: {
        display: "flex",
        flexDirection: "column",
        gap: "12px",
    }
});
