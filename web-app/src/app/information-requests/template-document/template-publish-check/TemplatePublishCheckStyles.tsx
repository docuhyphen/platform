import {makeStyles, tokens} from "@fluentui/react-components";

export const useTemplatePublishCheckStyles = makeStyles({
    check: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXS,
        maxHeight: "12rem",
        overflowY: "auto",
    },
    muted: {
        color: tokens.colorNeutralForeground3,
    },
    list: {
        listStyleType: "none",
        margin: 0,
        padding: 0,
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXXS,
    },
    problem: {
        color: tokens.colorPaletteRedForeground1,
        textAlign: "start",
        justifyContent: "flex-start",
        height: "auto",
        minHeight: tokens.lineHeightBase300,
        whiteSpace: "normal",
    },
});
