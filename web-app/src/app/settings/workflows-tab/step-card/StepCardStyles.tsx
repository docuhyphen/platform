import {makeStyles, tokens} from "@fluentui/react-components";

export const useStepCardStyles = makeStyles({
    card: {
        minWidth: 0,
    },

    cardHeader: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: "0.5rem",
        padding: "0.625rem 1rem",
        background: tokens.colorNeutralBackground2,
        position: "sticky",
        top: 0,
        zIndex: 2,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusMedium,
    },

    cardBody: {
        paddingTop: tokens.spacingVerticalM,
        paddingRight: tokens.spacingHorizontalL,
        paddingBottom: tokens.spacingVerticalL,
        paddingLeft: tokens.spacingHorizontalL,
        display: "flex",
        flexDirection: "column",
        gap: "0.875rem",
    },

    fieldGroup: {
        display: "grid",
        gridTemplateColumns: "minmax(0, 1fr) minmax(0, 1fr)",
        gap: "0.75rem",
        "@media (max-width: 900px)": {
            gridTemplateColumns: "1fr",
        },
    },

    field: {
        display: "flex",
        flexDirection: "column",
        gap: "4px",
        minWidth: 0,
    },

    fullWidth: {
        gridColumn: "1 / -1",
    },

    outcomeRow: {
        display: "flex",
        gap: tokens.spacingVerticalS,
        flexDirection: "column"
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
        minWidth: 0,
    },

    outcomeFieldRow: {
        display: "flex",
        gap: "4px",
        flexWrap: "wrap",
        minWidth: 0,
    },

    outcomeEmitSelect: {
        flex: "1 1 10rem",
        minWidth: 0,
    },

    quorumRow: {
        display: "flex",
        gap: "4px",
        flexWrap: "wrap",
        minWidth: 0,
    },

    quorumNInput: {
        width: "4rem",
        flexShrink: 0,
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
        minWidth: 0,
    },

    conditionValueSelect: {
        flex: "1 1 8rem",
        minWidth: 0,
    },

    conditionValueInput: {
        flex: "1 1 8rem",
        minWidth: 0,
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

