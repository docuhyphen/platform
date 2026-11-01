import {makeStyles, tokens} from "@fluentui/react-components";

export const useIdpSessionPolicyCardStyles = makeStyles({
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
