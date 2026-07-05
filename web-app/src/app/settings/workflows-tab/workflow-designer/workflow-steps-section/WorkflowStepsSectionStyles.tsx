import {makeStyles} from "@fluentui/react-components";

export const useWorkflowStepsSectionStyles = makeStyles({
    stepList: {
        display: "flex",
        flexDirection: "column",
        gap: "0.75rem",
    },

    stepListHeader: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        marginBottom: "0.25rem",
    },

    noStepsText: {
        color: "var(--colorNeutralForeground3)",
    },
});
