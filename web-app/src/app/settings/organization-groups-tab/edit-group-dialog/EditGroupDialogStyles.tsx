import {makeStyles, tokens} from "@fluentui/react-components";

export const useEditGroupDialogStyles = makeStyles({

    dialogTitleContainer: {
        display: "flex",
        flexDirection: "row",
        justifyContent: "space-between",
        alignItems: "center",
        flexWrap: "wrap",
        gap: "8px",
    },

    dialogContentContainer: {
        margin: "8px 0",
        display: "flex",
        flexDirection: "column",
        gap: "16px",
        minHeight: "520px",
        // The members table can be very wide; let the body scroll
        // horizontally inside the dialog instead of pushing content out
        // of the surface on phones.
        overflowX: "auto",
        // Cap the fixed min-height on tiny screens so the dialog can
        // still fit in the viewport (combined with the global
        // .fui-DialogSurface max-height cap in index.css).
        "@media (max-width: 768px)": {
            minHeight: "auto",
        },
    },

    appUserPermissionListContainer: {
        display: "flex",
        flexDirection: "column",
        gap: "8px",
        paddingTop: "8px"

    },

    mainDivider: {
        width: "300px",
        maxWidth: "100%",
        "@media (max-width: 768px)": {
            width: "100%",
        },
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