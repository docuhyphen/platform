import {makeStyles, tokens} from "@fluentui/react-components";

export const useStepCardStyles = makeStyles({
    card: {
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusMedium,
        overflow: "hidden",
    },

    cardHeader: {
        display: "flex",
        alignItems: "center",
        gap: "0.5rem",
        padding: "0.625rem 1rem",
        background: tokens.colorNeutralBackground2,
        cursor: "pointer",
        userSelect: "none",
    },

    stepNumber: {
        color: tokens.colorNeutralForeground3,
        flexShrink: 0,
    },

    headerTitle: {
        flex: 1,
    },

    cardBody: {
        padding: "1rem",
        display: "flex",
        flexDirection: "column",
        gap: "0.875rem",
    },

    fieldGroup: {
        display: "grid",
        gridTemplateColumns: "1fr 1fr",
        gap: "0.75rem",
        "@media (max-width: 600px)": {
            gridTemplateColumns: "1fr",
        },
    },

    field: {
        display: "flex",
        flexDirection: "column",
        gap: "4px",
    },

    fullWidth: {
        gridColumn: "1 / -1",
    },

    outcomeRow: {
        display: "grid",
        gridTemplateColumns: "1fr 1fr",
        gap: "0.75rem",
    },

    sectionLabel: {
        color: tokens.colorNeutralForeground3,
        marginBottom: "4px",
    },

    removeButton: {
        marginLeft: "auto",
        color: tokens.colorStatusDangerForeground1,
    },
});

