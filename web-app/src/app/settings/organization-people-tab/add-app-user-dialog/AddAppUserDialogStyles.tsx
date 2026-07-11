import {makeStyles, tokens} from "@fluentui/react-components";

export const useAddAppUserDialogStyles = makeStyles({
    dialogContentContainer: {
        margin: `${tokens.spacingVerticalS} 0`,
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalL
    },
    errorContainer: {
        minHeight: "20px",
        color: tokens.colorStatusDangerForeground1,
        fontSize: "12px",
        lineHeight: "20px"
    }
});