import {makeStyles, tokens} from "@fluentui/react-components";

export const useInformationRequestStructuredResponseWorkspaceStyles = makeStyles({
    root: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalL,
        minHeight: 0,
        height: "100%",
        boxSizing: "border-box",
    },
    mutedPanel: {
        color: tokens.colorNeutralForeground3,
        padding: tokens.spacingHorizontalM,
        borderRadius: tokens.borderRadiusMedium,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
    },
    toolbar: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        gap: tokens.spacingHorizontalM,
        flexWrap: "wrap",
    },
    groupControls: {
        display: "flex",
        gap: tokens.spacingHorizontalS,
        flexWrap: "wrap",
    },
    scrollRegion: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalL,
        minHeight: 0,
        overflowY: "auto",
        overflowX: "hidden",
        scrollbarGutter: "stable",
        paddingRight: tokens.spacingHorizontalXS,
    },
    occurrenceBlock: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalM,
        padding: tokens.spacingHorizontalL,
        borderRadius: tokens.borderRadiusMedium,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        backgroundColor: tokens.colorNeutralBackground1,
    },
    occurrenceHeader: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        gap: tokens.spacingHorizontalM,
        flexWrap: "wrap",
    },
    occurrenceTitle: {
        fontWeight: tokens.fontWeightSemibold,
    },
    requirementGrid: {
        display: "grid",
        gridTemplateColumns: "repeat(auto-fit, minmax(min(100%, 280px), 1fr))",
        gap: tokens.spacingHorizontalM,
    },
    requirement: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalM,
        minWidth: 0,
    },
    prompt: {
        fontWeight: tokens.fontWeightSemibold,
    },
    helpText: {
        color: tokens.colorNeutralForeground3,
    },
    notice: {
        color: tokens.colorNeutralForeground3,
    },
    error: {
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
