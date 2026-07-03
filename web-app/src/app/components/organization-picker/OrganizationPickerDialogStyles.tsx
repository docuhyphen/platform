import {makeStyles} from "@fluentui/react-components";

export const useOrganizationPickerDialogStyles = makeStyles({
    content: {
        margin: "8px 0",
        display: "flex",
        flexDirection: "column",
        gap: "12px",
    },
    orgList: {
        display: "flex",
        flexDirection: "column",
        gap: "8px",
        marginTop: "8px",
        width: "100%",
    },
    orgButton: {
        justifyContent: "flex-start",
        width: "100%",
    },
});
