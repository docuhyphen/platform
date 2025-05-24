import {makeStyles} from "@fluentui/react-components";

export const useEditGroupDialogStyles = makeStyles({

    dialogTitleContainer: {
        display: "flex",
        flexDirection: "row",
        justifyContent: "space-between",
    },

    dialogContentContainer: {
        margin: "8px 0",
        display: "flex",
        flexDirection: "column",
        gap: "16px",
        minHeight: "520px"
    },

    appUserPermissionListContainer: {
        display: "flex",
        flexDirection: "column",
        gap: "8px",
        paddingTop: "8px"

    },
});