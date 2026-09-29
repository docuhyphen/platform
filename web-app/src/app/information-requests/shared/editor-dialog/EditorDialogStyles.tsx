import {makeStyles, tokens} from "@fluentui/react-components";

export const useEditorDialogStyles = makeStyles({
    surface: {
        maxWidth: `min(560px, calc(100vw - (${tokens.spacingHorizontalXXL} * 2)))`,
    },
    wideSurface: {
        maxWidth: `min(880px, calc(100vw - (${tokens.spacingHorizontalXXL} * 2)))`,
        width: "100%",
    },
    content: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
        maxHeight: "70vh",
        overflowY: "auto",
    },
    actions: {
        display: "flex",
        justifyContent: "flex-end",
        gap: tokens.spacingHorizontalS,
        flexWrap: "wrap",
    },
});
