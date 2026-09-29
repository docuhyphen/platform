import {makeStyles, tokens} from "@fluentui/react-components";

export const useTemplateRequirementRowStyles = makeStyles({
    row: {
        display: "flex",
        alignItems: "flex-start",
        justifyContent: "space-between",
        gap: tokens.spacingHorizontalS,
        padding: `${tokens.spacingVerticalS} ${tokens.spacingHorizontalS}`,
        borderRadius: tokens.borderRadiusMedium,
        backgroundColor: tokens.colorNeutralBackground2,
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
        flex: "1 1 16rem",
    },
    facts: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
        flexWrap: "wrap",
    },
    key: {
        color: tokens.colorNeutralForeground3,
        fontFamily: tokens.fontFamilyMonospace,
        fontSize: tokens.fontSizeBase200,
        overflowWrap: "anywhere",
    },
    problem: {
        color: tokens.colorPaletteRedForeground1,
    },
});
