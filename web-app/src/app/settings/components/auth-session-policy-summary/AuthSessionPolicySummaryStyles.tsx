import {makeStyles, tokens} from "@fluentui/react-components";

export const useAuthSessionPolicySummaryStyles = makeStyles({
    introduction: {
        display: "block",
        marginBottom: tokens.spacingVerticalM,
    },
    grid: {
        display: "grid",
        gridTemplateColumns: "repeat(2, minmax(0, 1fr))",
        gap: tokens.spacingHorizontalM,
        marginBottom: tokens.spacingVerticalL,
        "@media (max-width: 700px)": {
            gridTemplateColumns: "1fr",
        },
    },
    card: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXS,
        padding: tokens.spacingHorizontalM,
        backgroundColor: tokens.colorNeutralBackground2,
        borderRadius: tokens.borderRadiusMedium,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
    },
    value: {
        color: tokens.colorBrandForeground1,
    },
    supportingText: {
        color: tokens.colorNeutralForeground3,
    },
});
