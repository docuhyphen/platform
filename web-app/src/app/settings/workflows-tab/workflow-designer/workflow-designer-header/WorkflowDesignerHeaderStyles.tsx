import {makeStyles, tokens} from "@fluentui/react-components";

export const useWorkflowDesignerHeaderStyles = makeStyles({
    topBar: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalM,
        marginBottom: tokens.spacingVerticalXS,
    },
    topBarLeft: {
        display: "flex",
        flexDirection: "column"
    },

    backNavButton: {
        marginBottom: tokens.spacingVerticalM
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
