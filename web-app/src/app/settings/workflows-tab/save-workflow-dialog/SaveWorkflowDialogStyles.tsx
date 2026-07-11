import {makeStyles, tokens} from "@fluentui/react-components";

export const useStyles = makeStyles({
    contentWrapper: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalL,
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
        gap: tokens.spacingHorizontalM,
    },
    reviewRow: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXXS,
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
        gap: tokens.spacingHorizontalXS,
    },
    stepItem: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
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
        gap: tokens.spacingHorizontalXS,
    },
});
