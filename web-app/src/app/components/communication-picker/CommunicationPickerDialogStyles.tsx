import {makeStyles, tokens} from "@fluentui/react-components";

export const useCommunicationPickerDialogStyles = makeStyles({
    dialogSurface: {
        maxWidth: "600px",
        width: "100%",
    },
    contentContainer: {
        display: "flex",
        flexDirection: "column",
        gap: "12px",
    },
    itemList: {
        display: "flex",
        flexDirection: "column",
        gap: "8px",
        maxHeight: "360px",
        overflowY: "auto",
    },
    itemSubject: {
        color: tokens.colorNeutralForeground3,
        fontStyle: "italic",
        overflow: "hidden",
        textOverflow: "ellipsis",
        whiteSpace: "nowrap",
    },
    itemSummary: {
        color: tokens.colorNeutralForeground2,
    },
    itemHeader: {
        display: "flex",
        alignItems: "center",
        gap: "8px",
    },
    noResults: {
        color: tokens.colorNeutralForeground3,
    },
});
