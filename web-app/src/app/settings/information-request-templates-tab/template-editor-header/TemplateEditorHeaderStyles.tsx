import {makeStyles, tokens} from "@fluentui/react-components";

export const useTemplateEditorHeaderStyles = makeStyles({
    header: {
        display: "flex",
        flexDirection: "column",
        alignItems: "flex-start",
        gap: tokens.spacingVerticalXS,
    },
    heading: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
        flexWrap: "wrap",
        minWidth: 0,
    },
    title: {
        margin: 0,
        overflowWrap: "anywhere",
    },
    key: {
        color: tokens.colorNeutralForeground3,
        fontFamily: tokens.fontFamilyMonospace,
        fontSize: tokens.fontSizeBase200,
        overflowWrap: "anywhere",
    },
});
