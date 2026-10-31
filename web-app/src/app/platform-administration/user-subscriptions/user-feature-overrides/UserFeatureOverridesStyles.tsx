import {makeStyles, tokens} from "@fluentui/react-components";

export const useUserFeatureOverridesStyles = makeStyles({
    overrides: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
        minWidth: 0,
    },
    muted: {
        color: tokens.colorNeutralForeground2,
    },
});
