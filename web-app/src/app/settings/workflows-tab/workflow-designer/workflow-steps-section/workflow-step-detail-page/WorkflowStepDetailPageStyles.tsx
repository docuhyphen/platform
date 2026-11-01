import {makeStyles, tokens} from "@fluentui/react-components";
import {workflowStepPageTransitionStyles} from "../workflowStepPageTransitionStyles.ts";

export const useWorkflowStepDetailPageStyles = makeStyles({
    ...workflowStepPageTransitionStyles,
    stepDetail: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
        minWidth: 0,
    },
});
