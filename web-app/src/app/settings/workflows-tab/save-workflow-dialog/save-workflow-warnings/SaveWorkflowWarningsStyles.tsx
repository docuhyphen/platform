import {makeStyles, tokens} from "@fluentui/react-components";

export const useSaveWorkflowWarningsStyles = makeStyles({
    warningList: {
        marginTop: tokens.spacingVerticalXS,
        marginBottom: 0,
        paddingLeft: tokens.spacingHorizontalL,
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXXS,
    },
});
