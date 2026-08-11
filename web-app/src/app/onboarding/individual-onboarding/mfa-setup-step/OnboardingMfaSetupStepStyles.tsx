import {makeStyles, tokens} from "@fluentui/react-components";

export const useOnboardingMfaSetupStepStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalL,
    },
    header: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
    },
    content: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalL,
    },
    actions: {
        display: "flex",
        justifyContent: "flex-end",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalM,
        paddingTop: tokens.spacingVerticalXL,
    },
    error: {
        color: tokens.colorPaletteRedForeground1,
    },
});
