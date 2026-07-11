import {makeStyles, tokens} from "@fluentui/react-components";

export const useCommunicationPickerDialogStyles = makeStyles({
    dialogSurface: {
        maxWidth: "600px",
        width: "100%",
    },
    contentContainer: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalM,
    },
    itemList: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalS,
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
        gap: tokens.spacingHorizontalS,
    },
    noResults: {
        color: tokens.colorNeutralForeground3,
    },
});
