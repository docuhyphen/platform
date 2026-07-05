import {makeStyles, tokens} from "@fluentui/react-components";

export const useWorkflowDesignerActionBarStyles = makeStyles({
    saveBar: {
        display: "flex",
        alignItems: "center",
        justifyContent: "flex-end",
        gap: "0.5rem",
        paddingTop: "1rem",
        borderTop: `1px solid ${tokens.colorNeutralStroke2}`,
    },
});
