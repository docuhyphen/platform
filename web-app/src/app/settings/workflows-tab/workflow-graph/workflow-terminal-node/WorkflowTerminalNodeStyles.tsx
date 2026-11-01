import {makeStyles, tokens} from "@fluentui/react-components";
import {workflowNodeHandleStyle, workflowNodeStateStyles} from "../workflowNodeStyleFragments.ts";

export const useWorkflowTerminalNodeStyles = makeStyles({
    terminalNode: {
        boxSizing: "border-box",
        width: "80px",
        height: "40px",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        borderRadius: tokens.borderRadiusCircular,
        border: `2px solid ${tokens.colorNeutralStroke1}`,
        backgroundColor: tokens.colorNeutralBackground3,
        fontSize: tokens.fontSizeBase300,
        fontWeight: tokens.fontWeightSemibold,
        color: tokens.colorNeutralForeground2,
    },

    handle: workflowNodeHandleStyle,

    ...workflowNodeStateStyles,
});
