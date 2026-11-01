import {makeStyles, tokens} from "@fluentui/react-components";

export const useExchangeDetailsTabSettingsStyles = makeStyles({
    settingsGrid: {
        display: "flex",
        flexDirection: "column",
        gridTemplateColumns: "1fr 1fr",
        gap: tokens.spacingHorizontalS,
    },
    settingItem: {
        display: "flex",
        flexDirection: "row",
        gap: tokens.spacingHorizontalSNudge,
        alignItems: "center",
    },
});
