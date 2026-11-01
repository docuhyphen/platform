import {makeStyles, shorthands, tokens} from "@fluentui/react-components";

export const useExchangeDocumentVersionPreviewDialogStyles = makeStyles({
    previewDialogSurface: {
        width: "90vw",
        maxWidth: "1100px",
        height: "90vh",
        position: "fixed",
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        margin: "auto",
        ...shorthands.padding(0),
    },
    previewCloseButton: {
        position: "absolute",
        top: tokens.spacingVerticalS,
        right: tokens.spacingHorizontalS,
        zIndex: 10,
    },
    previewDialogBody: {
        display: "flex",
        flexDirection: "column",
        height: "100%",
        ...shorthands.padding(0),
    },
    previewDialogContent: {
        flexGrow: 1,
        display: "flex",
        flexDirection: "column",
        ...shorthands.padding(0),
        overflow: "hidden",
    },
});
