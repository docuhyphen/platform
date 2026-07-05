import {makeStyles, tokens} from "@fluentui/react-components";

export const useStyles = makeStyles({
    contentWrapper: {
        display: "flex",
        flexDirection: "column",
        gap: "1rem",
    },
    mutedText: {
        color: tokens.colorNeutralForeground3,
    },
    mutedSpan: {
        color: tokens.colorNeutralForeground3,
    },
    reviewSection: {
        display: "flex",
        flexDirection: "column",
        gap: "0.75rem",
    },
    reviewRow: {
        display: "flex",
        flexDirection: "column",
        gap: "2px",
    },
    reviewLabel: {
        color: tokens.colorNeutralForeground3,
        fontSize: "11px",
        fontWeight: "600",
        textTransform: "uppercase",
        letterSpacing: "0.04em",
    },
    stepList: {
        display: "flex",
        flexDirection: "column",
        gap: "4px",
    },
    stepItem: {
        display: "flex",
        alignItems: "center",
        gap: "0.5rem",
    },
    stepNumber: {
        width: "20px",
        height: "20px",
        borderRadius: tokens.borderRadiusCircular,
        backgroundColor: tokens.colorNeutralBackground3,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        fontSize: "11px",
        fontWeight: "600",
        flexShrink: 0,
    },
    tagRow: {
        display: "flex",
        flexWrap: "wrap",
        gap: "4px",
    },
});
