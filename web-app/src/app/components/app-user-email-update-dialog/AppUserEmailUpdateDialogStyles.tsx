import {makeStyles, tokens} from "@fluentui/react-components";

export const useAppUserEmailUpdateDialogStyles = makeStyles({
    dialogContentContainer: {
        margin: "8px 0",
        display: "flex",
        flexDirection: "column",
        gap: "16px"
    },
    errorContainer: {
        minHeight: "20px",
        color: tokens.colorStatusDangerForeground1,
        fontSize: "12px",
        lineHeight: "20px"
    }
});