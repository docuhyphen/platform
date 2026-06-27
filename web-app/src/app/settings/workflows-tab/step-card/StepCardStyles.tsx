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

    outcomeFieldColumn: {
        display: "flex",
        flexDirection: "column",
        gap: "4px",
    },

    outcomeFieldRow: {
        display: "flex",
        gap: "4px",
        flexWrap: "wrap",
    },

    outcomeEmitSelect: {
        flex: "1",
        minWidth: "10rem",
    },

    quorumRow: {
        display: "flex",
        gap: "4px",
    },

    quorumNInput: {
        width: "4rem",
    },

    notificationRow: {
        display: "flex",
        gap: "6px",
        alignItems: "center",
        flexWrap: "wrap",
    },

    notificationHint: {
        color: "var(--colorNeutralForeground3)",
    },

    conditionHint: {
        color: "var(--colorNeutralForeground3)",
    },

    conditionRow: {
        display: "flex",
        gap: "0.5rem",
        flexWrap: "wrap",
        alignItems: "center",
    },

    conditionFieldCombobox: {
        flex: "1 1 10rem",
        minWidth: "0",
    },

    conditionOperatorSelect: {
        flex: "1 1 10rem",
    },

    conditionValueSelect: {
        flex: "1 1 8rem",
    },

    conditionValueInput: {
        flex: "1 1 8rem",
    },

    entityPickerCombobox: {
        flex: "1 1 8rem",
        minWidth: "0",
    },

    sublabel: {
        color: "var(--colorNeutralForeground3)",
        marginLeft: "0.5rem",
        fontSize: "0.8em",
    },

    waitDescription: {
        color: "var(--colorNeutralForeground3)",
    },

    conditionPreview: {
        color: "var(--colorNeutralForeground3)",
    },
});

