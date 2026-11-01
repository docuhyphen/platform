import {makeStyles, tokens} from "@fluentui/react-components";

export const useWorkflowMetadataFormStyles = makeStyles({
    formGrid: {
        display: "flex",
        gap: tokens.spacingVerticalS,
        flexDirection: "column"
    },

    formField: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXS,
    },

    fullWidth: {
        gridColumn: "1 / -1",
    },
});
