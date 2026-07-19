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
});
