import {makeStyles, tokens} from "@fluentui/react-components";

export const useWorkflowDesignerHeaderStyles = makeStyles({
    topBar: {
        display: "flex",
        alignItems: "center",
        gap: "0.75rem",
        marginBottom: "0.25rem",
    },

    titleGroup: {
        display: "flex",
        alignItems: "baseline",
        gap: "0.5rem",
        minWidth: 0,
        flex: "1 1 auto",
        overflow: "hidden",
    },

    workflowName: {
        color: tokens.colorNeutralForeground3,
        whiteSpace: "nowrap",
        overflow: "hidden",
        textOverflow: "ellipsis",
        minWidth: 0,
    },

    helpButton: {
        marginLeft: "auto",
        flexShrink: 0,
    },
});
