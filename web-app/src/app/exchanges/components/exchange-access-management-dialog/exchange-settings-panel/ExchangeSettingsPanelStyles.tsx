import {makeStyles, tokens} from "@fluentui/react-components";

export const useExchangeSettingsPanelStyles = makeStyles({
    root: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalM,
    },
    requireSignIn: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
        "@media (max-width: 640px)": {
            flexDirection: "column",
            alignItems: "stretch",
        },
    },
    actionGroup: {
        display: "flex",
        justifyContent: "flex-end",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalS,
        flexDirection: "column",
        "@media (max-width: 640px)": {
            justifyContent: "flex-start",
        },
    },
});
