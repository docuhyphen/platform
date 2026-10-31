import {makeStyles, tokens} from "@fluentui/react-components";

export const useStructuredResponseOccurrenceStyles = makeStyles({
    occurrenceBlock: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalM,
        padding: tokens.spacingHorizontalL,
        borderRadius: tokens.borderRadiusMedium,
        border: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
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
    nested: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
        paddingLeft: tokens.spacingHorizontalM,
        borderLeft: `${tokens.strokeWidthThick} solid ${tokens.colorNeutralStroke2}`,
        minWidth: 0,
    },
    requirementGrid: {
        display: "grid",
        gridTemplateColumns: "repeat(auto-fit, minmax(min(100%, 280px), 1fr))",
        gap: tokens.spacingHorizontalM,
    },
});
