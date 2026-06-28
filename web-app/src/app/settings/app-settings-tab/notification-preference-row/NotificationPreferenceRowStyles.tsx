import {makeStyles, shorthands, tokens} from "@fluentui/react-components";

export const useNotificationPreferenceRowStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXS,
        ...shorthands.padding(tokens.spacingVerticalM, 0),
        ...shorthands.borderBottom("1px", "solid", tokens.colorNeutralStroke2),
    },
    topRow: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: tokens.spacingHorizontalL,
        "@media (max-width: 640px)": {
            alignItems: "stretch",
            flexDirection: "column",
            gap: tokens.spacingVerticalS,
        },
    },
    title: {
        color: tokens.colorNeutralForeground1,
    },
    dropdown: {
        width: "240px",
        flexShrink: 0,
        "@media (max-width: 640px)": {
            width: "100%",
        },
    },
    selectedChannels: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalXS,
        minWidth: 0,
        overflow: "hidden",
    },
    emptySelection: {
        color: tokens.colorNeutralForeground3,
    },
    description: {
        color: tokens.colorNeutralForeground3,
        maxWidth: "620px",
    },
});
