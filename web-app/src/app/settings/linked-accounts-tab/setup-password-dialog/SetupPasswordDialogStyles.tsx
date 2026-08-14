import {makeStyles, tokens} from "@fluentui/react-components";

export const useSetupPasswordDialogStyles = makeStyles({
    content: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
    },
    guidance: {
        color: tokens.colorNeutralForeground3,
    },
    successContent: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
    },
});
