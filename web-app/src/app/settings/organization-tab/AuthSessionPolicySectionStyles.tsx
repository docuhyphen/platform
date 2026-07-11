import {makeStyles, tokens} from "@fluentui/react-components";

export const useAuthSessionPolicySectionStyles = makeStyles({
    divider: {
        marginTop: tokens.spacingVerticalXL,
        marginBottom: tokens.spacingVerticalM,
    },
    loadingWrapper: {
        padding: tokens.spacingHorizontalS,
    },
    error: {
        color: tokens.colorStatusDangerForeground1,
        marginBottom: tokens.spacingVerticalS,
    },
    effectiveGrid: {
        display: "grid",
        gridTemplateColumns: "auto auto auto",
        columnGap: tokens.spacingHorizontalL,
        rowGap: tokens.spacingVerticalXS,
        marginBottom: tokens.spacingVerticalL,
        padding: tokens.spacingHorizontalS,
        background: tokens.colorNeutralBackground2,
        borderRadius: tokens.borderRadiusMedium,
    },
    noIdpText: {
        color: tokens.colorNeutralForeground3,
    },
    idpCard: {
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusMedium,
        padding: tokens.spacingHorizontalM,
        marginBottom: tokens.spacingVerticalM,
    },
    idpCardHeader: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
        marginBottom: tokens.spacingVerticalS,
    },
    inputsGrid: {
        display: "grid",
        gridTemplateColumns: "1fr 1fr 1fr 1fr",
        gap: tokens.spacingHorizontalM,
        marginBottom: tokens.spacingVerticalS,
    },
    inputLabel: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXS,
    },
    draftError: {
        color: tokens.colorStatusDangerForeground1,
        marginBottom: tokens.spacingVerticalS,
    },
    actionRow: {
        display: "flex",
        gap: tokens.spacingHorizontalS,
    },
});
