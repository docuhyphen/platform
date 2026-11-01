import {makeStyles, tokens} from "@fluentui/react-components";

export const useCommunicationsListContentStyles = makeStyles({
    scrollableContent: {
        flex: 1,
        minHeight: 0,
        overflowY: "auto",
        overflowX: "hidden",
        overscrollBehavior: "contain",
        paddingInline: tokens.spacingHorizontalS,
        boxSizing: "border-box",
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalL,
    },
    errorText: {
        color: tokens.colorPaletteRedForeground1,
    },
    emptyText: {
        color: tokens.colorNeutralForeground3,
    },
});
