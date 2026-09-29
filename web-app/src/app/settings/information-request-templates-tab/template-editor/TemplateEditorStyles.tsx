import {makeStyles, tokens} from "@fluentui/react-components";

export const useTemplateEditorStyles = makeStyles({
    editor: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
        height: "100%",
        minHeight: 0,
        minWidth: 0,
        paddingInline: tokens.spacingHorizontalS,
        boxSizing: "border-box",
    },
});
