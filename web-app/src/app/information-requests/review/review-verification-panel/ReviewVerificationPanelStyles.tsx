import {makeStyles, tokens} from "@fluentui/react-components";

export const useReviewVerificationPanelStyles = makeStyles({
    panel: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
        padding: tokens.spacingHorizontalL,
        borderRadius: tokens.borderRadiusMedium,
        border: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
        backgroundColor: tokens.colorNeutralBackground1,
        boxSizing: "border-box",
        maxWidth: "100%",
    },
    mutedText: {
        color: tokens.colorNeutralForeground3,
    },
    action: {
        alignSelf: "flex-start",
    },
});
