import {makeStyles, tokens} from "@fluentui/react-components";

export const useAuditEventCardListStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
        width: "100%",
        minWidth: 0,
    },
    cardList: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
    },
    card: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXS,
        padding: tokens.spacingVerticalS,
        borderRadius: tokens.borderRadiusMedium,
        backgroundColor: tokens.colorNeutralBackground3,
        cursor: "pointer",
        textAlign: "left",
        border: "none",
        width: "100%",
        minWidth: 0,
    },
    cardTopRow: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: tokens.spacingHorizontalS,
        flexWrap: "wrap",
    },
    cardTopRowLeft: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
        minWidth: 0,
    },
    eventTypeLabel: {
        fontWeight: tokens.fontWeightSemibold,
    },
    occurredAt: {
        color: tokens.colorNeutralForeground3,
        flexShrink: 0,
    },
    cardBottomRow: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalS,
        flexWrap: "wrap",
    },
    cardBottomRowItem:{
      display: "flex",
        gap: tokens.spacingHorizontalS,
        alignItems: "center"
    },
    truncate: {
        overflow: "hidden",
        textOverflow: "ellipsis",
        whiteSpace: "nowrap",
    },
    arrowIcon: {
        flexShrink: 0,
        color: tokens.colorNeutralForeground3,
    },
    outcomeSuccess: {
        color: tokens.colorPaletteGreenForeground1,
    },
    outcomeFailure: {
        color: tokens.colorPaletteRedForeground1,
    },
    outcomeNeutral: {
        color: tokens.colorNeutralForeground3,
    },
    footer: {
        display: "flex",
        justifyContent: "center",
        paddingTop: tokens.spacingVerticalS,
    },
    emptyText: {
        color: tokens.colorNeutralForeground3,
        padding: tokens.spacingVerticalL,
        textAlign: "center",
    },
});
