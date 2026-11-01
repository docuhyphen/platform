import {makeStyles, tokens} from "@fluentui/react-components";

export const useGroupDeleteDialogStyles = makeStyles({
    errorMessage: {
        minHeight: "20px",
        color: tokens.colorStatusDangerForeground1,
        fontSize: tokens.fontSizeBase200,
        lineHeight: tokens.lineHeightBase300,
    },
});
