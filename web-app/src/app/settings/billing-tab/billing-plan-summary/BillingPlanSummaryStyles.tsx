import {makeStyles, tokens} from "@fluentui/react-components";

export const useBillingPlanSummaryStyles = makeStyles({
    planCard: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
        width: "100%",
        padding: `${tokens.spacingVerticalL} ${tokens.spacingHorizontalL}`,
        border: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusMedium,
        backgroundColor: tokens.colorNeutralBackground1,
        textAlign: "left",
    },
    planHeader: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: tokens.spacingHorizontalM,
        '@media (max-width: 480px)': {
            alignItems: "flex-start",
            flexDirection: "column",
        },
    },
    planTitle: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXXS,
    },
    planFacts: {
        display: "grid",
        gridTemplateColumns: "repeat(3, 1fr)",
        gap: tokens.spacingHorizontalM,
        color: tokens.colorNeutralForeground3,
        '@media (max-width: 640px)': {
            gridTemplateColumns: "1fr",
        },
    },
    usageRow: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXS,
    },
    usageLabels: {
        display: "flex",
        justifyContent: "space-between",
        gap: tokens.spacingHorizontalS,
    },
});
