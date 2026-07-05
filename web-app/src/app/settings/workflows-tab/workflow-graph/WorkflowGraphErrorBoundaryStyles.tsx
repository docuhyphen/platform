import {makeStyles} from "@fluentui/react-components";

export const useWorkflowGraphErrorBoundaryStyles = makeStyles({
    fallback: {
        display: "flex",
        flexDirection: "column",
        gap: "8px",
        padding: "12px 0",
    },
});
