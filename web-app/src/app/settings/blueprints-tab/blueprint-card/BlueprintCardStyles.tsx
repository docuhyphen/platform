import {GriffelStyle, makeStyles, tokens} from "@fluentui/react-components";

export const blueprintCardStyles = {
    blueprintCard: {
        border: `1px solid ${tokens.colorNeutralStroke1}`,
        borderRadius: tokens.borderRadiusXLarge,
        padding: `${tokens.spacingVerticalM} ${tokens.spacingHorizontalL}`,
        display: "flex",
        justifyContent: "space-between",
        alignItems: "flex-start",
        gap: tokens.spacingHorizontalS,
    },
    blueprintCardContent: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXS,
        flex: "1",
        minWidth: 0,
    },
    summaryText: {
        color: tokens.colorNeutralForeground2,
    },
    badgeRow: {
        display: "flex",
        gap: tokens.spacingHorizontalSNudge,
        flexWrap: "wrap",
        alignItems: "center",
    },
} satisfies Record<string, GriffelStyle>;

export const useBlueprintCardStyles = makeStyles(blueprintCardStyles);
