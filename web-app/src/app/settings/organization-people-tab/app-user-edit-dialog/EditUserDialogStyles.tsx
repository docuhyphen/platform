import {makeStyles} from "@fluentui/react-components";

export const useEditUserDialogStyles = makeStyles({
    dialogContentContainer: {

        margin: "8px 0",
        display: "flex",
        flexDirection: "column",
        gap: "16px"
    },
    errorContainer: {
        minHeight: "20px",
        color: "red",
        fontSize: "12px",
        lineHeight: "20px"
    }
});