import {makeStyles, tokens} from "@fluentui/react-components";

export const useAlternativeMfaMethodDialogStyles = makeStyles({
    methodList: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
    },
});
