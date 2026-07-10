import {makeStyles, tokens} from "@fluentui/react-components";

export const useAuditEventsSectionStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        flex: 1,
        gap: tokens.spacingVerticalM,
        height: "100%",
        minWidth: 0,
        minHeight: 0,
        overflow: "hidden",
    },
    filterPanel: {
        display: "flex",
        flexWrap: "wrap",
        flexShrink: 0,
        gap: tokens.spacingHorizontalM,
        alignItems: "flex-end",
    },
    filterField: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXXS,
        minWidth: "200px",
    },
    selectedCategories: {
        display: "flex",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalXS,
    },
    errorText: {
        color: tokens.colorPaletteRedForeground1,
        flexShrink: 0,
    },
});
