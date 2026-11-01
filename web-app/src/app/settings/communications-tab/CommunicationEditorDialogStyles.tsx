import {makeStyles, tokens} from "@fluentui/react-components";

export const useCommunicationEditorDialogStyles = makeStyles({
    dialogSurface: {
        maxWidth: "680px",
        width: "100%",
    },
    errorSpan: {
        color: tokens.colorPaletteRedForeground1,
        fontSize: tokens.fontSizeBase200,
        marginTop: tokens.spacingVerticalS,
        display: "block",
    },
});
