import {makeStyles, tokens} from "@fluentui/react-components";

export const useAddGroupDialogStyles = makeStyles({
    dialogContentContainer: {
        margin: `${tokens.spacingVerticalS} 0`,
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalL,
        overflowX: "auto",
    },
    errorMessage: {
        color: tokens.colorStatusDangerForeground1,
        marginBottom: tokens.spacingVerticalMNudge,
    },
});
