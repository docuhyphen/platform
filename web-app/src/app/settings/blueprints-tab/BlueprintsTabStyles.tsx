import {makeStyles, tokens} from "@fluentui/react-components";
import {blueprintCardStyles} from "./blueprint-card/BlueprintCardStyles.tsx";
import {blueprintListStatusTextStyles} from "./blueprints-list-content/BlueprintsListContentStyles.tsx";

export const useBlueprintEditorStyles = makeStyles({
    dialogSurface: {
        maxWidth: "600px",
        width: "100%",
    },
    errorSpan: {
        color: tokens.colorPaletteRedForeground1,
        fontSize: tokens.fontSizeBase200,
        marginTop: tokens.spacingVerticalS,
        display: "block",
    },
});

export const useTemplatesTabStyles = makeStyles({
    tabContainer: {
        display: "flex",
        flexDirection: "row",
        gap: tokens.spacingHorizontalL,
        width: "100%",
        minWidth: 0,
        flexWrap: "wrap",
    },
    header: {
        width: "100%",
        display: "flex",
        marginBottom: tokens.spacingVerticalL,
        justifyContent: "space-between",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalS,
    },
    outerContainer: {
        display: "flex",
        flexDirection: "column",
        gap: 0,
        width: "100%",
        height: "100%",
        minHeight: 0,
    },
    stickyBlock: {
        background: tokens.colorNeutralBackground1,
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalS,
        paddingBottom: tokens.spacingVerticalS,
        flexShrink: 0,
        paddingInline: tokens.spacingHorizontalS,
        boxSizing: "border-box",
    },
    ...blueprintCardStyles,
    ...blueprintListStatusTextStyles,
});
