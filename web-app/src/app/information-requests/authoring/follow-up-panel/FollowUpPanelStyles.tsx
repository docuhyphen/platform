import {makeStyles, tokens} from "@fluentui/react-components";

export const useFollowUpPanelStyles = makeStyles({
    panel: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
    },
    row: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalS,
    },
    text: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXXS,
    },
    supplement: {
        display: "flex",
        alignItems: "flex-end",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalM,
    },
    supplementReason: {
        flexGrow: 1,
        minWidth: "200px",
    },
    muted: {
        color: tokens.colorNeutralForeground2,
    },
});
