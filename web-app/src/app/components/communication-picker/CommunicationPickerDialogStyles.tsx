import {makeStyles, tokens} from "@fluentui/react-components";

export const useCommunicationPickerDialogStyles = makeStyles({
    dialogSurface: {
        maxWidth: "600px",
        width: "100%",
    },
    contentContainer: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
    },
    itemList: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
        maxHeight: "360px",
        overflowY: "auto",
    },
    noResults: {
        color: tokens.colorNeutralForeground3,
    },
});
