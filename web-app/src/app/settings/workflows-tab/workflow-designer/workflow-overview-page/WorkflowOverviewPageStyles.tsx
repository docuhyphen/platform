import {makeStyles, tokens} from "@fluentui/react-components";

export const useWorkflowOverviewPageStyles = makeStyles({
    formSectionCards: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
        minWidth: 0,
        paddingBottom: tokens.spacingVerticalM,
    },
});
