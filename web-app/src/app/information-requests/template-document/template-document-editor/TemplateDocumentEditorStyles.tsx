import {makeStyles, tokens} from "@fluentui/react-components";

export const useTemplateDocumentEditorStyles = makeStyles({
    editor: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
        minWidth: 0,
        minHeight: 0,
        flex: 1,
    },
    tabs: {
        overflowX: "auto",
        flexShrink: 0,
    },
    panel: {
        flex: 1,
        minHeight: 0,
        overflowY: "auto",
        overflowX: "hidden",
        paddingBottom: tokens.spacingVerticalM,
    },
});
