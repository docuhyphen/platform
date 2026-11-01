import {makeStyles, tokens} from "@fluentui/react-components";

export const useWorkflowCardStyles = makeStyles({
    row: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalSNudge,
        padding: `${tokens.spacingVerticalM} ${tokens.spacingHorizontalL}`,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusMedium,
        minWidth: 0,
        overflow: "hidden",
    },
    topRow: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
        minWidth: 0,
    },
    triggerText: {
        flexShrink: 0,
        color: tokens.colorNeutralForeground3,
    },
    middleRow: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
        minWidth: 0,
    },
    description: {
        flex: 1,
        overflow: "hidden",
        textOverflow: "ellipsis",
        whiteSpace: "nowrap",
        minWidth: 0,
        color: tokens.colorNeutralForeground2,
    },
    middleActions: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalXS,
        flexShrink: 0,
    },
});
