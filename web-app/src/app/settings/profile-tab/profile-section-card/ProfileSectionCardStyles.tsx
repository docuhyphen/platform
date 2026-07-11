import {makeStyles, shorthands, tokens} from "@fluentui/react-components";

export const useProfileSectionCardStyles = makeStyles({
    card: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalL,
        minHeight: "100%",
        ...shorthands.padding(tokens.spacingHorizontalXL),
        ...shorthands.border("1px", "solid", tokens.colorNeutralStroke2),
        boxShadow: "none",
        backgroundColor: "transparent",
    },

    header: {
        display: "flex",
        justifyContent: "space-between",
        gap: tokens.spacingHorizontalM,
        alignItems: "flex-start",
        flexWrap: "wrap",
    },

    copyBlock: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalSNudge,
    },

    action: {
        display: "flex",
        alignItems: "center",
    },

    description: {
        color: tokens.colorNeutralForeground3,
    },

    content: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalL,
    }
});
