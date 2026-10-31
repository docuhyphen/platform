import {makeStyles, tokens} from "@fluentui/react-components";

export const useStructuredResponseSaveFooterStyles = makeStyles({
    error: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXS,
        color: tokens.colorPaletteRedForeground1,
    },
    actions: {
        display: "flex",
        justifyContent: "flex-end",
        gap: tokens.spacingHorizontalS,
        flexWrap: "wrap",
        flexShrink: 0,
    },
});
