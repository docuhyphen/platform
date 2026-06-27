import {makeStyles, tokens} from "@fluentui/react-components";

export const useAuthSessionPolicySectionStyles = makeStyles({
    divider: {
        marginTop: "20px",
        marginBottom: "12px",
    },

    loadingWrapper: {
        padding: "8px",
    },

    error: {
        color: tokens.colorStatusDangerForeground1,
        marginBottom: "8px",
    },

    effectiveGrid: {
        display: "grid",
        gridTemplateColumns: "auto auto auto",
        columnGap: "16px",
        rowGap: "4px",
        marginBottom: "16px",
        padding: "8px",
        background: tokens.colorNeutralBackground2,
        borderRadius: "4px",
    },

    noIdpText: {
        color: tokens.colorNeutralForeground3,
    },

    idpCard: {
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: "4px",
        padding: "12px",
        marginBottom: "12px",
    },

    idpCardHeader: {
        display: "flex",
        alignItems: "center",
        gap: "8px",
        marginBottom: "8px",
    },

    inputsGrid: {
        display: "grid",
        gridTemplateColumns: "1fr 1fr 1fr 1fr",
        gap: "12px",
        marginBottom: "8px",
    },

    inputLabel: {
        display: "flex",
        flexDirection: "column",
        gap: "4px",
    },

    draftError: {
        color: tokens.colorStatusDangerForeground1,
        marginBottom: "8px",
    },

    actionRow: {
        display: "flex",
        gap: "8px",
    },
});
