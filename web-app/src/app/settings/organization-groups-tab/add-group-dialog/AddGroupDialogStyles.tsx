import {makeStyles, tokens} from "@fluentui/react-components";

export const useAddGroupDialogStyles = makeStyles({
    dialogContentContainer: {
        margin: "8px 0",
        display: "flex",
        flexDirection: "column",
        gap: "16px",
        // Allow the members table to scroll horizontally inside the
        // dialog on narrow screens (paired with the global mobile
        // DialogSurface size cap in index.css).
        overflowX: "auto",
    },
    errorMessage: {
        color: tokens.colorStatusDangerForeground1,
        marginBottom: "10px",
    },
    addMembersField: {
        marginBottom: "12px",
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