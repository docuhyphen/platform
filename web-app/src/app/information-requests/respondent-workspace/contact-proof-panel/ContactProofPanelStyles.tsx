import {makeStyles, tokens} from "@fluentui/react-components";

export const useContactProofPanelStyles = makeStyles({
    proofPanel: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
        width: "min(100%, 520px)",
        padding: tokens.spacingHorizontalXL,
        borderRadius: tokens.borderRadiusMedium,
        border: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
        backgroundColor: tokens.colorNeutralBackground1,
        boxSizing: "border-box",
    },
    proofActions: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
    },
    mutedText: {
        color: tokens.colorNeutralForeground3,
    },
});
