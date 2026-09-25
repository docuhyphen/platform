import {makeStyles, tokens} from "@fluentui/react-components";

export const useSubmissionAttestationCardStyles = makeStyles({
    card: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
        padding: tokens.spacingVerticalM,
        border: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusMedium,
        minWidth: 0,
    },
    header: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
        flexWrap: "wrap",
    },
    detail: {
        color: tokens.colorNeutralForeground3,
    },
    actions: {
        display: "flex",
        gap: tokens.spacingHorizontalS,
        flexWrap: "wrap",
    },
});
