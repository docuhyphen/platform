import {makeStyles, shorthands, tokens} from "@fluentui/react-components";

export const useProfileTabStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXL,
        width: "100%",
        minWidth: 0,
        maxWidth: "1120px",
    },

    cardGrid: {
        display: "grid",
        gridTemplateColumns: "repeat(2, minmax(0, 1fr))",
        gap: tokens.spacingHorizontalL,
        alignItems: "stretch",
        "@media (max-width: 900px)": {
            gridTemplateColumns: "1fr",
        },
    },

    infoStack: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalL,
    },

    infoRow: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "flex-start",
        gap: tokens.spacingHorizontalL,
        flexWrap: "wrap",
    },

    infoLabel: {
        color: tokens.colorNeutralForeground3,
    },

    contentBlock: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalSNudge,
        minWidth: 0,
        flex: 1
    },

    helperText: {
        color: tokens.colorNeutralForeground3,
    },

    securityStack: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalM,
    },

    securityPanel: {
        display: "flex",
        justifyContent: "space-between",
        gap: tokens.spacingHorizontalS,
        alignItems: "center",
        flexWrap: "wrap",
    },

    securityNote: {
        display: "flex",
        alignItems: "flex-start",
        gap: tokens.spacingHorizontalMNudge,
        ...shorthands.padding(tokens.spacingVerticalM, tokens.spacingHorizontalM),
        ...shorthands.borderRadius(tokens.borderRadiusLarge),
        ...shorthands.border("1px", "solid", tokens.colorNeutralStroke2),
        color: tokens.colorNeutralForeground2,
    },

    notificationCardContent: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalM,
    }
});
