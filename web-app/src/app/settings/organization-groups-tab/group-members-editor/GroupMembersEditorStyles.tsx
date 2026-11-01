import {makeStyles, tokens} from "@fluentui/react-components";

export const useGroupMembersEditorStyles = makeStyles({
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
