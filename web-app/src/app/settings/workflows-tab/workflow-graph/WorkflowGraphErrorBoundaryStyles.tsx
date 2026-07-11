import {tokens, makeStyles} from "@fluentui/react-components";

export const useWorkflowGraphErrorBoundaryStyles = makeStyles({
    fallback: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalS,
        padding: `${tokens.spacingVerticalM} 0`,
    },
});
