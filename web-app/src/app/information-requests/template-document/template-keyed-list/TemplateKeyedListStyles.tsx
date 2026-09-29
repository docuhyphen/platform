import {makeStyles, tokens} from "@fluentui/react-components";

export const useTemplateKeyedListStyles = makeStyles({
    panel: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
        minWidth: 0,
    },
    list: {
        listStyleType: "none",
        margin: 0,
        padding: 0,
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXS,
    },
    item: {
        display: "flex",
        alignItems: "flex-start",
        justifyContent: "space-between",
        gap: tokens.spacingHorizontalS,
        padding: `${tokens.spacingVerticalS} ${tokens.spacingHorizontalM}`,
        border: `1px solid ${tokens.colorNeutralStroke1}`,
        borderRadius: tokens.borderRadiusMedium,
        flexWrap: "wrap",
    },
    highlighted: {
        outline: `${tokens.strokeWidthThick} solid ${tokens.colorBrandStroke1}`,
    },
    summary: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXXS,
        minWidth: 0,
        flex: "1 1 14rem",
    },
    muted: {
        color: tokens.colorNeutralForeground3,
    },
    problem: {
        color: tokens.colorPaletteRedForeground1,
    },
    addButton: {
        alignSelf: "flex-start",
    },
});
