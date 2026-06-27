import {makeStyles, tokens} from "@fluentui/react-components";

export const useGroupDeleteDialogStyles = makeStyles({
    errorMessage: {
        minHeight: "20px",
        color: tokens.colorStatusDangerForeground1,
        fontSize: "12px",
        lineHeight: "20px",
    },
});
