import {makeStyles, tokens} from "@fluentui/react-components";

export const useAddGroupDialogStyles = makeStyles({
    dialogContentContainer: {
        margin: `${tokens.spacingVerticalS} 0`,
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalL,
        // Allow the members table to scroll horizontally inside the
        // dialog on narrow screens (paired with the global mobile
        // DialogSurface size cap in index.css).
        overflowX: "auto",
    },
    errorMessage: {
        color: tokens.colorStatusDangerForeground1,
        marginBottom: tokens.spacingVerticalMNudge,
    },
    addMembersField: {
        marginBottom: tokens.spacingVerticalM,
    },
    memberNameCell: {
        maxWidth: "180px",
        overflow: "hidden",
        textOverflow: "ellipsis",
        whiteSpace: "nowrap",
    },
    memberEmailCell: {
        maxWidth: "220px",
        overflow: "hidden",
        textOverflow: "ellipsis",
        whiteSpace: "nowrap",
    },
    roleHeaderCell: {
        width: "120px",
    },
    actionsHeaderCell: {
        width: "56px",
    },
    roleTableCell: {
        width: "120px",
    },
    actionsTableCell: {
        width: "56px",
    },
    roleDropdown: {
        minWidth: "90px",
        maxWidth: "110px",
    },
});