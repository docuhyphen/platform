import {makeStyles, tokens} from "@fluentui/react-components";

export const useStepUpPanelStyles = makeStyles({
    panel: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
        padding: tokens.spacingHorizontalL,
        borderRadius: tokens.borderRadiusMedium,
        border: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
        backgroundColor: tokens.colorNeutralBackground1,
        boxSizing: "border-box",
    },
    mutedText: {
        color: tokens.colorNeutralForeground3,
    },
    accountAction: {
        alignSelf: "flex-start",
    },
});
