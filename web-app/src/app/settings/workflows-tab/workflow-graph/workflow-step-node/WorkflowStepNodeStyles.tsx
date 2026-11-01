import {makeStyles, tokens} from "@fluentui/react-components";
import {workflowNodeHandleStyle, workflowNodeStateStyles} from "../workflowNodeStyleFragments.ts";

export const useWorkflowStepNodeStyles = makeStyles({
    stepNode: {
        boxSizing: "border-box",
        width: "180px",
        height: "88px",
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXS,
        padding: `${tokens.spacingVerticalMNudge} ${tokens.spacingHorizontalMNudge}`,
        borderRadius: tokens.borderRadiusMedium,
        border: `2px solid ${tokens.colorNeutralStroke1}`,
        backgroundColor: tokens.colorNeutralBackground1,
        overflow: "hidden",
    },

    headerRow: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalSNudge,
        minHeight: "16px",
    },

    kindLabel: {
        fontSize: tokens.fontSizeBase200,
        lineHeight: tokens.lineHeightBase200,
        color: tokens.colorNeutralForeground3,
        textTransform: "uppercase",
        letterSpacing: "0.03em",
        whiteSpace: "nowrap",
        overflow: "hidden",
        textOverflow: "ellipsis",
    },

    title: {
        fontSize: tokens.fontSizeBase300,
        lineHeight: tokens.lineHeightBase300,
        fontWeight: tokens.fontWeightSemibold,
        color: tokens.colorNeutralForeground1,
        whiteSpace: "nowrap",
        overflow: "hidden",
        textOverflow: "ellipsis",
        paddingBottom: tokens.spacingVerticalXXS,
    },

    details: {
        fontSize: tokens.fontSizeBase200,
        lineHeight: tokens.lineHeightBase200,
        color: tokens.colorNeutralForeground3,
        whiteSpace: "nowrap",
        overflow: "hidden",
        textOverflow: "ellipsis",
    },

    stateBadge: {
        fontSize: tokens.fontSizeBase100,
        lineHeight: tokens.lineHeightBase100,
        fontWeight: tokens.fontWeightSemibold,
        whiteSpace: "nowrap",
        overflow: "hidden",
        textOverflow: "ellipsis",
    },

    handle: workflowNodeHandleStyle,

    ...workflowNodeStateStyles,
});
