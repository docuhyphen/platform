import {makeStyles} from "@fluentui/react-components";

export const useEditGroupDialogStyles = makeStyles({
    dialogContentContainer: {
        margin: "8px 0",
        display: "flex",
        flexDirection: "column",
        gap: "16px"
    },

    nameSection: {
        display: "flex",
        flexDirection: "column",
    }
});