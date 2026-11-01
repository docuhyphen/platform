import {makeStyles, tokens} from "@fluentui/react-components";

export const useWorkflowInstanceFilterBarStyles = makeStyles({
    filterBar: {
        display: "flex",
        gap: tokens.spacingHorizontalM,
        justifyContent: "end",
        alignItems: "center",
        flexWrap: "wrap",
    },
    statusFilterSelect: {
        minWidth: "10rem",
    },
});
