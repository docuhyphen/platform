import {makeStyles, tokens} from "@fluentui/react-components";

export const usePrivacyPanelStyles = makeStyles({
    panel: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalL,
        maxWidth: "1100px",
        minWidth: 0,
    },
    heading: {
        marginTop: 0,
        marginBottom: 0,
    },
    muted: {
        color: tokens.colorNeutralForeground2,
    },
});
