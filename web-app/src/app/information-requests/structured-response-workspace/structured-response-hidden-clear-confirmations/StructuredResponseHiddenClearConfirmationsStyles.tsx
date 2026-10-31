import {makeStyles, tokens} from "@fluentui/react-components";

export const useStructuredResponseHiddenClearConfirmationsStyles = makeStyles({
    clearConfirmations: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalS,
        padding: tokens.spacingHorizontalM,
        borderRadius: tokens.borderRadiusMedium,
        border: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
        backgroundColor: tokens.colorNeutralBackground2,
    },
    prompt: {
        fontWeight: tokens.fontWeightSemibold,
    },
});
