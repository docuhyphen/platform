import {makeStyles, tokens} from "@fluentui/react-components";

export const useWorkflowDesignerFormPagesStyles = makeStyles({
    formPage: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
        minWidth: 0,
        minHeight: "100%",
    },
});
