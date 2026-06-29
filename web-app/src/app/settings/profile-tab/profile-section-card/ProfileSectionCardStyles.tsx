import {makeStyles, shorthands, tokens} from "@fluentui/react-components";

export const useProfileSectionCardStyles = makeStyles({
    card: {
        display: "flex",
        flexDirection: "column",
        gap: "18px",
        minHeight: "100%",
        ...shorthands.padding("20px"),
        ...shorthands.border("1px", "solid", tokens.colorNeutralStroke2),
        boxShadow: "none",
        backgroundColor: "transparent",
    },

    header: {
        display: "flex",
        justifyContent: "space-between",
        gap: "12px",
        alignItems: "flex-start",
        flexWrap: "wrap",
    },

    copyBlock: {
        display: "flex",
        flexDirection: "column",
        gap: "6px",
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
        gap: "16px",
    }
});
