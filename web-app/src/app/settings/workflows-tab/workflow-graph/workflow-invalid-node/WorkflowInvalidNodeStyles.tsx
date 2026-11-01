import {makeStyles, tokens} from "@fluentui/react-components";
import {workflowNodeHandleStyle} from "../workflowNodeStyleFragments.ts";

export const useWorkflowInvalidNodeStyles = makeStyles({
    invalidNode: {
        boxSizing: "border-box",
        width: "180px",
        height: "88px",
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalSNudge,
        padding: `${tokens.spacingVerticalS} ${tokens.spacingHorizontalMNudge}`,
        borderRadius: tokens.borderRadiusMedium,
        border: `2px dashed ${tokens.colorStatusDangerBorder1}`,
        backgroundColor: tokens.colorStatusDangerBackground1,
        color: tokens.colorStatusDangerForeground1,
        fontSize: tokens.fontSizeBase200,
        overflow: "hidden",
    },

    handle: workflowNodeHandleStyle,
});
