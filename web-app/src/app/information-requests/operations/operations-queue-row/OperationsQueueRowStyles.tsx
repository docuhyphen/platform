import {makeStyles, tokens} from "@fluentui/react-components";

export const useOperationsQueueRowStyles = makeStyles({
    row: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalM,
        padding: tokens.spacingVerticalM,
        borderRadius: tokens.borderRadiusMedium,
        border: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
        backgroundColor: tokens.colorNeutralBackground1,
        flexWrap: "wrap",
    },
    summary: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXXS,
        flex: 1,
        minWidth: "220px",
    },
    detail: {
        color: tokens.colorNeutralForeground3,
    },
    exception: {
        color: tokens.colorStatusWarningForeground1,
    },
});
