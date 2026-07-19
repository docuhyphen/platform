import {makeStyles, tokens} from "@fluentui/react-components";

export const usePeopleSummaryPanelStyles = makeStyles({
    root: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalM,
    },
    personCard: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusMedium,
        padding: `${tokens.spacingVerticalMNudge} ${tokens.spacingHorizontalM}`,
        gap: tokens.spacingHorizontalS,
        "@media (max-width: 640px)": {
            flexDirection: "column",
            alignItems: "flex-start",
        },
    },
    personDetails: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXS,
        minWidth: 0,
    },
    secondaryText: {
        color: tokens.colorNeutralForeground3,
        overflowWrap: "anywhere",
    },
    replacementActions: {
        display: "flex",
        flexDirection: "column",
        alignItems: "flex-start",
        gap: tokens.spacingVerticalXS,
    },
});
